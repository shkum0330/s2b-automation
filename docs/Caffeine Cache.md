# Caffeine Cache
## 1. 개요
Caffeine은 Ben Manes가 개발한 Java 8+ 기반 고성능 인메모리 캐시 라이브러리로, Google Guava Cache의 후속·대체 라이브러리로 설계되었다. GitHub에서 "high performance, near optimal caching library"로 소개하며, Kafka, Solr, Cassandra, HBase, Neo4j 등 대규모 오픈소스 프로젝트에서 실제로 채택 중이다. Spring Boot 2.x 이후 공식 캐시 구현체로도 지원되어 Spring Cache 추상화와 자연스럽게 통합된다.

Caffeine은 `ConcurrentMap`과 유사하지만 확실히 다른 부분이 있다. `ConcurrentMap`은 명시적으로 제거할 때까지 모든 항목을 유지하는 반면, Caffeine Cache는 메모리 사용량을 제한하기 위해 항목을 자동으로 제거(evict)한다.

## 2. 핵심 기능
Caffeine은 빌더 패턴으로 다음 기능을 선택적으로 조합할 수 있다.

- 항목 자동 로딩 (선택적 비동기)
- 빈도(frequency)와 최근성(recency) 기반의 사이즈 제거 — W-TinyLFU
- 마지막 접근 또는 마지막 쓰기 기준의 시간 기반 만료
- 첫 번째 stale 요청 시 비동기 리프레시
- 키의 weak reference / 값의 weak·soft reference 래핑
- 제거 항목에 대한 알림 리스너
- 외부 리소스로의 쓰기 전파
- 캐시 접근 통계 수집 (hitRate, missRate, evictionCount 등)

JSR-107 JCache 및 Guava 어댑터를 확장 모듈로 제공해 마이그레이션도 용이하다.


## 3. W-TinyLFU 제거 정책
아래 다이어그램은 Caffeine 내부의 W-TinyLFU 구조를 시각적으로 보여준다.

<p align="center">
  <img src="https://github-production-user-asset-6210df.s3.amazonaws.com/102662024/552025764-e18f39ec-8c4b-4530-984f-ded94cd7534e.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAVCODYLSA53PQK4ZA%2F20260219%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260219T093942Z&X-Amz-Expires=300&X-Amz-Signature=847ee9aac629979c05ebe41ea5f160e148b21d0f39019f644d0d6d50a55cb1e7&X-Amz-SignedHeaders=host" width="75%" alt="Caffeine Cache W-TinyLFU Diagram">
</p>

### 3.1. 전통적 LRU의 한계
LRU(Least Recently Used)는 단순하고 성능이 양호하지만, 캐시에 현재 존재하는 항목의 이력만으로 미래를 예측하기 때문에 full scan 같은 워크로드에서 히트율이 급격히 떨어진다. 현대 캐시는 최신성(recency)과 빈도(frequency)를 동시에 고려해야 한다.
### 2. 세 구역 구조
Caffeine 2.0+에서 채택한 W-TinyLFU는 다이어그램과 같이 세 구역으로 나뉜다.

**① Admission Window (소형 LRU)**
- 새로 들어온 항목(New Entry)이 처음 배치되는 작은 영역 (전체 용량의 약 1~2%).
- 내부는 LRU로 관리된다. burst 접근 패턴에서 빈도가 아직 쌓이지 않은 항목을 보호하여, 연속적 cache miss를 방지한다.
- Window가 가득 차면 LRU에 의해 밀려난 항목이 TinyLFU 필터로 진입한다.

**② TinyLFU Admission Filter**
- Window에서 나온 항목(Candidate)이 Main 영역에 들어가려면, 빈도 스케치(FrequencySketch)에 기록된 추정 빈도가 현재 Probation 세그먼트의 제거 대상(Victim)보다 높아야 승인(Admit)된다.
- Candidate 빈도 ≥ Victim 빈도 → **Admit**: Candidate를 Probation에 삽입, Victim을 제거.
- Candidate 빈도 < Victim 빈도 → **Reject**: Candidate를 버리고 기존 캐시를 유지.
- 이것이 W-TinyLFU가 일반 LFU와 다른 결정적 차이점이다. "빈도가 낮은 victim을 골라내는 것"이 아니라, **"새로 들어오는 항목이 기존 항목보다 가치 있는지 판별하는 입장 심사(admission)"** 역할을 한다.

**③ Main Space: Segmented LRU**
- 승인된 항목은 **Probation(수습) 세그먼트**에 배치된다.
- Probation 내 항목이 재접근되면 **Protected 세그먼트**(전체 용량의 약 80%)로 승격(Promote)된다.
- Protected가 가득 차면 LRU에 의해 가장 오래된 항목을 Probation으로 강등(Demote)한다.
- Probation에서 밀려나는 항목이 TinyLFU 필터에서 다시 Candidate와 경쟁하며, 패배하면 최종 제거(Evict)된다.
### 3.3 CountMinSketch (빈도 스케치)
빈도 추정의 핵심은 `FrequencySketch` 클래스에 구현된 **4-bit CountMinSketch**이다. 이 확률적 자료구조의 특징은 다음과 같다.

- 항목이 접근될 때 여러 해시 함수(spread, rehash)로 카운터 배열의 여러 위치를 증가시킨다.
- 빈도 조회 시 해당 항목의 4개 카운터 중 **최솟값**을 반환하여 오차를 최소화한다.
- 각 `long` 값(64비트)이 16개의 4-bit 카운터를 저장하므로, 하나의 캐시 엔트리에 대한 카운터가 단일 CPU 캐시 라인에 들어와 메모리 접근 효율이 높다.
- 캐시 엔트리당 약 8바이트 증가로 매우 컴팩트하다.
- 너비 10000, 깊이 4인 스케치로 약 40KB만 사용하면서 1~2% 이내의 상대 오차를 달성한다.
### 3.4 Aging
빈도 스케치가 과거 패턴에 고착되지 않도록, 관찰된 이벤트 수가 `sampleSize`에 도달하면 `reset()` 메서드가 호출되어 모든 카운터를 절반으로 나눈다. 이를 통해 과거에 인기가 높았지만 더 이상 접근되지 않는 항목의 빈도가 점진적으로 감쇄되어, 변화하는 접근 패턴에 적응한다.
### 3.5 적응형 정책 (Hill Climbing)
Caffeine은 Window와 Main 영역의 크기 비율을 런타임에 동적으로 조정한다. 샘플 기간의 히트율을 직전 히트율과 비교해, 히트율이 개선되면 같은 방향으로 조정을 계속하고 악화되면 반대로 전환하는 **hill-climbing 알고리즘**을 사용한다. 수동 튜닝 없이 워크로드에 맞게 자동 최적화된다.
### 3.6 성능 효과
W-TinyLFU는 순수 LRU 대비 heavy-tailed 워크로드에서 히트율을 10~30% 개선하며, ARC·LIRS 같은 고급 정책과 대등한 히트율을 제공하면서도 비거주 키(non-resident key) 메타데이터를 유지할 필요가 없어 메모리 오버헤드가 낮다.


## 4. 캐시 채우기 전략 (Population)
### 4.1 수동 (Manual)
```java
Cache<String, DataObject> cache = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .maximumSize(100)
    .build();

cache.getIfPresent("A");                          // 존재하면 반환, 없으면 null
cache.get("A", k -> DataObject.get("Data for A")); // 없으면 원자적으로 계산 후 저장
```

`get(key, Function)`은 여러 스레드가 동시에 같은 키를 요청해도 한 번만 계산하므로 `getIfPresent`보다 선호된다.
### 4.2 동기 로딩 (Synchronous Loading)
```java
LoadingCache<String, DataObject> cache = Caffeine.newBuilder()
    .maximumSize(100)
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build(k -> DataObject.get("Data for " + k));

DataObject obj = cache.get("A");
Map<String, DataObject> map = cache.getAll(List.of("A","B","C"));
```

빌드 시 로더 함수를 등록하면, `get(key)`만으로 자동 로딩된다.
### 4.3 비동기 로딩 (Asynchronous Loading)
```java
AsyncLoadingCache<String, DataObject> cache = Caffeine.newBuilder()
    .maximumSize(100)
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .buildAsync(k -> DataObject.get("Data for " + k));

cache.get("A").thenAccept(obj -> { /* 사용 */ });
```

`CompletableFuture`를 반환해 논블로킹 워크플로를 지원한다.


## 5. 제거 전략 (Eviction)
### 5.1 크기 기반
- **개수 제한**: `maximumSize(long)` — 최대 항목 수
- **가중치 제한**: `maximumWeight(long)` + `weigher((key, value) -> weight)` — 항목별 가중치 합계 기준
### 5.2 시간 기반
| 모드 | 메서드 | 기준 시점 |
|------|--------|----------|
| 접근 후 만료 | `expireAfterAccess(duration)` | 마지막 읽기 또는 쓰기 이후 |
| 쓰기 후 만료 | `expireAfterWrite(duration)` | 마지막 쓰기 이후 |
| 커스텀 만료 | `expireAfter(Expiry)` | 항목별로 개별 계산 |

변동형 만료를 위해 내부적으로 계층적 타이머 휠(Hierarchical Timer Wheel)을 사용하며, `{64, 64, 32, 4, 1}` 버킷 구성의 5단계 계층으로 ~1초부터 ~6.5일까지를 O(1)에 처리한다.
### 5.3 참조 기반
- `weakKeys()`: 키를 WeakReference로 래핑
- `weakValues()`: 값을 WeakReference로 래핑
- `softValues()`: 값을 SoftReference로 래핑. 메모리 부족 시 GC 대상


## 6. 리프레시 (refreshAfterWrite)
`refreshAfterWrite(duration)`을 설정하면, 지정 시간이 지난 후 첫 번째 요청이 들어왔을 때 **기존(stale) 값을 즉시 반환**하면서 백그라운드에서 비동기적으로 새 값을 로딩한다.

`expireAfterWrite`와의 주요 차이점은 다음과 같다.

| | expireAfterWrite | refreshAfterWrite |
|--|--|--|
| 만료 시 동작 | 새 값 계산까지 **호출 스레드 블로킹** | **즉시 stale 값 반환**, 비동기 로딩 |
| 리로딩 실패 시 | 예외 전파 | stale 값 유지, 다음 요청 시 재시도 |
| 필수 조건 | Cache/LoadingCache 모두 가능 | **LoadingCache 전용** (CacheLoader 필수) |

***
## 7. 동시성 설계
Caffeine의 동시성 처리는 데이터베이스의 커밋 로그 아이디어에서 영감을 받았다.

**읽기 — Striped Ring Buffer**: 읽기 이벤트는 스레드별 해시로 선택된 스트라이프 링 버퍼에 기록된다. 링 버퍼가 가득 차면 비동기 드레인이 예약되고, 해당 버퍼의 이후 추가는 공간이 확보될 때까지 버려진다. 캐시 값 자체는 호출자에게 정상 반환되며, 버퍼 유실은 W-TinyLFU의 핫 엔트리 식별에 유의미한 영향을 주지 않는다.

**쓰기 — Concurrent Queue**: 쓰기는 데이터 손실이 허용되지 않으므로 즉시 드레인을 예약하는 동시 큐를 사용한다.

읽기·쓰기 버퍼 모두 **다수 생산자 / 단일 소비자(MPSC)** 패턴으로 소비되어 단순하고 효율적인 알고리즘이 적용된다. 벤치마크에서 **읽기는 CPU 수에 비례하여 선형 확장**된다.


## 8. Caffeine vs Redis 비교
Caffeine은 단일 JVM 프로세스 내 로컬 캐시이고, Redis는 별도 서버의 네트워크 기반 분산 캐시 + 데이터 스토어라서 목적과 트레이드오프가 근본적으로 다르다.

| 구분 | Caffeine | Redis |
|------|---------|-------|
| 위치 | JVM 힙 내부 (in-process) | 외부 독립 서버/클러스터 |
| 접근 경로 | 메서드 호출 → 메모리 직접 접근, **~1ms 이하** | 네트워크 I/O 포함, **~5-10ms** |
| 공유 범위 | 인스턴스마다 별도 캐시, 인스턴스 간 공유 불가 | 여러 인스턴스가 동일 Redis를 공유 |
| 데이터 모델 | Key-Value (Java 객체 그대로) | String, List, Set, Sorted Set, Hash, Stream 등 다양한 자료구조 |
| 확장성 | JVM 힙 메모리 한도, GC 영향 | 수백 GB + 클러스터링으로 수평 확장 가능 |
| 일관성 | 인스턴스별 캐시 → stale 허용 전제 | 공유 소스 → 상대적으로 일관성 유지 쉬움 |
| 내구성 | JVM 종료 시 소멸 | 스냅샷/AOF로 디스크 영속화 가능 |
| 언어 지원 | Java/JVM 전용 | 언어 불문 (다양한 클라이언트) |
| 운영 복잡도 | 라이브러리 추가만으로 끝, 별도 인프라 불필요 | 서버 운영, 모니터링, 장애 복구 필요 |
| 추가 기능 | 캐시 전용 | Pub/Sub, Stream, 트랜잭션, Lua 스크립트 등 |
### 8.1 Caffeine이 더 적합한 경우
- 단일 인스턴스 또는 소규모 인스턴스이고, 인스턴스 간 캐시 일관성이 크게 중요하지 않을 때
- 같은 노드에서 동일 데이터를 반복 조회하는 워크로드에서 최저 지연이 중요할 때 (코드/설정 정보, feature flag, 참조 데이터 등)
- Redis 인프라 없이 애플리케이션 안에서만 간단히 캐시 레이어를 두고 싶을 때
- 초기 단계 서비스에서 인프라 비용과 운영 복잡도를 최소화하고 싶을 때
### 8.2 Redis가 더 적합한 경우
- 로드밸런서 뒤에 여러 인스턴스가 있고, 캐시/세션 데이터를 공유해야 할 때
- MSA 환경에서 서비스 간 데이터 공유, Pub/Sub 메시징이 필요할 때
- 랭킹(Sorted Set), 카운터, 스트림 같은 Redis의 고급 자료구조가 필요한 경우
- 캐시 크기가 JVM 힙을 초과하거나 클러스터링/복제 등 고가용성이 필요한 경우
- 캐시 데이터의 디스크 영속화(persistence)가 필요한 경우
### 8.3 캐시 테크트리
좋은 캐시 전략의 진화 경로는 다음과 같다.

```
Local Cache (Caffeine) → Distributed Cache (Redis) → Hybrid Cache (Caffeine + Redis)
```

처음에는 Caffeine으로 시작하고, 인프라가 확장되면 Redis를 도입하고, 궁극적으로는 두 계층을 함께 사용하는 것이 가장 흔한 패턴이다.


## 9. 멀티 레벨 캐시 아키텍처 (L1: Caffeine + L2: Redis)
실무에서는 Caffeine과 Redis를 "둘 중 하나"가 아니라 **L1/L2 멀티 레벨 캐시**로 함께 사용하는 패턴이 가장 많이 쓰인다.
### 9.1 조회 흐름
각 계층별 지연 시간

| 계층 | 지연 시간 | 인스턴스 간 공유 | 메모리 비용 |
|------|----------|--------------|-----------|
| L1: Caffeine (Local) | ~1ms | No | JVM 힙 사용 |
| L2: Redis (Distributed) | ~5-10ms | Yes | 별도 인프라 |
| Database | ~50-100ms | Yes | N/A |

```
Request → L1(Caffeine) Hit? → 반환 (최고 속도)
             ↓ Miss
         L2(Redis) Hit? → L1에 저장 후 반환
             ↓ Miss
         Database 조회 → L2에 저장 → L1에 저장 → 반환
```
### 9.2 Spring Boot 구현
`MultiLevelCacheManager`를 직접 구현하여 두 CacheManager를 조합하는 방식이 일반적이다:

```java
@Configuration
@EnableCaching
public class MultiLevelCacheConfig {

    @Bean
    public CacheManager localCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(300, TimeUnit.SECONDS)
            .recordStats());
        return manager;
    }

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(3600))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }

    @Bean
    @Primary
    public CacheManager multiLevelCacheManager(
            CacheManager localCacheManager, CacheManager redisCacheManager) {
        return new MultiLevelCacheManager(localCacheManager, redisCacheManager);
    }
}
```

커스텀 `MultiLevelCache`의 핵심 로직은 `get()` 시 L1 → L2 → DB 순으로 조회하고, `put()` 시 양쪽 모두에 쓰는 **write-through** 전략이다.
### 9.3 캐시 무효화 (인스턴스 간 동기화)
여러 인스턴스가 각각 L1(Caffeine)을 가지고 있으므로, 데이터 변경 시 다른 인스턴스의 로컬 캐시도 비워야 한다. **Redis Pub/Sub**을 통한 무효화 이벤트 브로드캐스트가 가장 일반적인 패턴이다.

```java
// 변경 발생 시 무효화 이벤트 발행
public void publishInvalidation(String cacheName, Object key) {
    CacheInvalidationEvent event = new CacheInvalidationEvent(cacheName, key.toString());
    redisTemplate.convertAndSend("cache:invalidation", event);
}

// 다른 인스턴스에서 수신하여 로컬 캐시 제거
@Override
public void onMessage(Message message, byte[] pattern) {
    CacheInvalidationEvent event = deserialize(message.getBody());
    Cache cache = localCacheManager.getCache(event.getCacheName());
    if (event.getKey() != null) {
        cache.evict(event.getKey());     // 특정 키 무효화
    } else {
        cache.clear();                   // 전체 캐시 클리어
    }
}
```

이 구조를 통해 읽기는 Caffeine 로컬 히트로 최소 지연을 달성하고, 쓰기/변경은 Redis Pub/Sub으로 모든 인스턴스에 전파하여 일관성을 유지한다.


***
## 10. Spring Boot 통합
### 10.1 의존성
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```
### 10.2 가장 간단한 설정 (application.yml)
```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500,expireAfterAccess=10m
```

`@EnableCaching`을 메인 클래스에 붙이면 바로 동작한다.
### 10.3 프로그래밍 방식 설정
캐시별 개별 TTL/사이즈가 필요한 프로덕션 환경에서는 `SimpleCacheManager`에 각각의 `CaffeineCache`를 등록하는 방식이 더 적합하다.

```java
@Bean
public CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(List.of(
        new CaffeineCache("users", Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .recordStats().build()),
        new CaffeineCache("products", Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(5_000)
            .recordStats().build())
    ));
    return manager;
}
```
### 10.4 모니터링
`recordStats()`를 활성화하고 spring-boot-starter-actuator + Micrometer가 있으면 캐시 메트릭이 자동 등록되어 Prometheus/Grafana 등에서 실시간 모니터링이 가능하다.


## 11. 사용 시 주의사항
- **maximumSize/maximumWeight 필수 설정**: 미설정 시 캐시가 무한히 커져 힙 메모리를 잠식할 수 있다.
- **제거의 비동기 특성**: `estimatedSize()`나 제거 리스너 호출이 지연될 수 있다. 정확한 크기를 확인하려면 `cleanUp()`을 먼저 호출해야 한다.
- **refreshAfterWrite는 LoadingCache 전용**: 빌드 시 `CacheLoader`를 등록하지 않으면 예외가 발생한다.
- **시간 기반 만료의 플랫폼 비용**: macOS에서 `System.nanoTime()`이 시스템 콜이라 쓰기 성능에 영향을 줄 수 있다. Linux 프로덕션 환경에서는 유저 스페이스 호출로 빠르게 처리된다.
- **로컬 캐시의 본질적 한계**: 서버 재시작 시 캐시 소멸, 인스턴스 간 동기화 없음. 일시적 stale 데이터를 허용할 수 있는 도메인에 적합하다.
- **L1 TTL은 L2 TTL보다 짧게**: 멀티 레벨 캐시 시 로컬 캐시 TTL을 분산 캐시보다 짧게 설정해 데이터 신선도를 확보한다.


## 12. 적용 사례

프로젝트에서 Spring Cache 어노테이션(`@Cacheable`) 중심이 아니라, Caffeine의 `Cache<K, V>`를 직접 주입해 상태 저장소처럼 사용하고 있다.

### 12.1 적용 배경
- 비동기 생성 요청은 즉시 응답(202) 후 `taskId`를 반환하고, 이후 폴링으로 결과를 조회한다.
- 이 흐름은 "메서드 결과 캐싱"보다 "작업 상태 관리(등록/조회/취소/만료)" 성격이 강하다.
- OAuth `state` 값은 1회성 검증 후 즉시 소비되어야 하므로, 키 단위 제어가 필수다.

### 12.2 구현 포인트
- `CacheConfig`에서 두 개의 캐시 빈을 분리한다.
  - `taskCache`: `Cache<String, CompletableFuture<?>>`
  - `oauthStateCache`: `Cache<String, Long>`
- `taskCache`는 `expireAfterWrite(10분)`, `maximumSize(1000)`으로 제한한다.
- `taskCache`의 `removalListener`에서 완료되지 않은 `CompletableFuture`를 `cancel(true)` 처리해, 만료/제거 시 불필요한 백그라운드 작업을 정리한다.
- `oauthStateCache`는 `expireAfterWrite(5분)`, `maximumSize(10_000)`으로 구성하고, 검증 시 `asMap().remove(state)`로 단건 소비한다.


### 12.3 정리
- 현재 요구사항(작업 수명주기 제어, 강제 취소, 1회성 state 소비)에는 `@Cacheable`보다 직접 `Cache<K, V>` 제어가 단순하고 명확하다.
- 향후 "도메인 조회 결과 캐싱" 요구가 커지면, 그때는 `@EnableCaching` + `@Cacheable` 계층을 별도로 도입해 공존시키는 방식이 적합하다.

<br></br>

## References
* [https://github.com/ben-manes/caffeine](https://github.com/ben-manes/caffeine)
* [https://highscalability.com/design-of-a-modern-cache/](https://highscalability.com/design-of-a-modern-cache/)
* [https://www.baeldung.com/java-caching-caffeine](https://www.baeldung.com/java-caching-caffeine)
* [https://www.baeldung.com/spring-boot-caffeine-cache](https://www.baeldung.com/spring-boot-caffeine-cache)
* [https://www.systemoverflow.com/learn/caching/eviction-policies/admission-policies-and-w-tinylfu-filtering-cache-pollution](https://www.systemoverflow.com/learn/caching/eviction-policies/admission-policies-and-w-tinylfu-filtering-cache-pollution)
* [https://adriacabeza.github.io/2024/07/12/caffeine-cache.html](https://adriacabeza.github.io/2024/07/12/caffeine-cache.html)
* [https://dev.to/anish-anantharaman/in-memory-caching-with-caffeine-why-you-dont-always-need-redis-right-away-3992](https://dev.to/anish-anantharaman/in-memory-caching-with-caffeine-why-you-dont-always-need-redis-right-away-3992)
* [https://oneuptime.com/blog/post/2026-01-29-multi-level-caching-spring-boot/view](https://oneuptime.com/blog/post/2026-01-29-multi-level-caching-spring-boot/view)
* [https://blog.csdn.net/Sihang_Xie/article/details/128919122](https://blog.csdn.net/Sihang_Xie/article/details/128919122)
* [https://arxiv.org/pdf/1512.00727.pdf](https://arxiv.org/pdf/1512.00727.pdf)
* [https://stackoverflow.com/questions/53659142/refreshafterwrite-requires-a-loadingcache-in-spring-boot-caffeine-application](https://stackoverflow.com/questions/53659142/refreshafterwrite-requires-a-loadingcache-in-spring-boot-caffeine-application)
* [https://stackoverflow.com/questions/79378878/springboot-cache-caffeine-micrometer-how-to-configure-multiple-caches-in-on](https://stackoverflow.com/questions/79378878/springboot-cache-caffeine-micrometer-how-to-configure-multiple-caches-in-on)
* [https://www.linkedin.com/posts/kevin-mirchandani-503176171_should-you-go-for-%F0%9D%90%91%F0%9D%90%9E%F0%9D%90%9D%F0%9D%90%A2%F0%9D%90%AC-%F0%9D%90%88%F0%9D%90%A7-%F0%9D%90%8C%F0%9D%90%9E%F0%9D%90%A6-activity-7184904680404054017-nwym](https://www.linkedin.com/posts/kevin-mirchandani-503176171_should-you-go-for-%F0%9D%90%91%F0%9D%90%9E%F0%9D%90%9D%F0%9D%90%A2%F0%9D%90%AC-%F0%9D%90%88%F0%9D%90%A7-%F0%9D%90%8C%F0%9D%90%9E%F0%9D%90%A6-activity-7184904680404054017-nwym)
* [https://dev.to/ari-ghosh/mastering-redis-the-in-memory-data-structure-store-you-need-to-know-4086](https://dev.to/ari-ghosh/mastering-redis-the-in-memory-data-structure-store-you-need-to-know-4086)
* [https://java.elitedev.in/java/advanced-spring-boot-caching-redis-caffeine-multi-level-architecture-with-performance-optimization-e65596d8/](https://java.elitedev.in/java/advanced-spring-boot-caching-redis-caffeine-multi-level-architecture-with-performance-optimization-e65596d8/)
* [https://jug.bg/2019/05/multiple-cache-configurations-with-caffeine-and-spring-boot/](https://jug.bg/2019/05/multiple-cache-configurations-with-caffeine-and-spring-boot/)
