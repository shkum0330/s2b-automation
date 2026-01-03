
Spring Data JPA를 사용하다 보면 `UPDATE`나 `DELETE` 쿼리를 직접 작성할 때 `@Modifying` 어노테이션을 필수적으로 붙여야 한다는 사실을 알게 된다. 왜 이 어노테이션이 필요한지, 내부적으로 어떤 일이 일어나는지 알아보자.

<br>

## 1. @Modifying이란 무엇인가?
`@Modifying`은 `@Query` 어노테이션으로 작성된 JPQL 또는 네이티브 쿼리가 **데이터 조회(SELECT)가 아닌 변경(INSERT, UPDATE, DELETE, DDL) 작업임**을 프레임워크에 명시하는 어노테이션이다.

```java
@Modifying // 없으면 예외 발생
@Query("UPDATE Member m SET m.age = m.age + 1 WHERE m.age >= :age")
int bulkAgePlus(@Param("age") int age);
```
<br>

## 2. 내부 동작 원리

왜 굳이 데이터 변경 쿼리라는 걸 알려줘야 할까? 그 이유는 **JDBC와 JPA의 실행 메커니즘 차이**에 있다.

### (1) JDBC 레벨의 실행 방식 차이

데이터베이스와 통신하는 가장 밑바닥인 JDBC 레벨에서, SQL을 실행하는 메서드는 크게 두 가지로 나뉜다.

1. **`executeQuery()`**: `SELECT` 구문을 실행할 때 사용한다. 결과로 **`ResultSet`(데이터 집합)**을 반환한다.
2. **`executeUpdate()`**: `INSERT`, `UPDATE`, `DELETE` 구문을 실행할 때 사용한다. 결과로 **`int`(변경된 행의 개수)**를 반환한다.

### (2) Spring Data JPA의 판단 로직

Spring Data JPA는 `@Query`에 적힌 구문을 분석하여 실행할 준비를 한다. 이때 기본 전략은 모든 @Query를 조회용 쿼리로 간주하는 것이다.

Hibernate 내부적으로 쿼리를 실행하는 과정은 다음과 같다.

1. 메서드 호출 시, Spring Data JPA는 해당 쿼리 실행 계획을 수립한다.
2. 이때 `@Modifying`이 붙어있지 않다면, JPA는 이를 조회 쿼리로 판단하고 `Query.getResultList()` 또는 `Query.getSingleResult()`를 호출하려고 시도한다.
3. 하지만 실제 쿼리가 `UPDATE`라면, DB 드라이버는 ResultSet을 반환하지 않는다.
4. JPA는 ResultSet을 기대했는데 없으므로 `InvalidDataAccessApiUsageException` (또는 `QueryExecutionRequestException`)을 발생시킨다.

반면, `@Modifying`이 붙어있다면 JPA는 조회가 아닌 것이라고 인지하고, `Query.executeUpdate()`를 호출하여 정상적으로 쿼리를 실행한다.

<br>

## 3. 영속성 컨텍스트와의 관계


### (1) 벌크 연산의 특징

JPA의 기본 철학은 "객체를 조회해서 값을 바꾸면, 트랜잭션 종료 시점에 DB에 반영한다(Dirty Checking)"이다. 이 과정은 모두 영속성 컨텍스트(1차 캐시)를 거친다.

하지만 `@Modifying`이 붙은 쿼리는 벌크 연산으로 취급되어, **Context를 갱신하지 않고, DB에 먼저 반영된다.** 여기서 **데이터 불일치**가 발생한다.

### (2) 불일치 시나리오

```text
[상황] 회원의 크레딧은 현재 100

1. memberRepository.findById(1L);
   -> DB에서 조회 후 영속성 컨텍스트에 'Member(id=1, credit=100)' 저장 및 반환
   
2. memberRepository.updateCredit(1L, 50); // @Modifying 쿼리 실행
   -> 영속성 컨텍스트의 관리 상태를 반영하지 않고 DB에 UPDATE 실행
   -> [결과] DB: 50원 / 영속성 컨텍스트: 100원 (서로 다름)

3. member.getCredit();
   -> 영속성 컨텍스트에 있는 객체를 그대로 가져옴
   -> [결과] 100원 출력 (DB는 50원인데 애플리케이션은 100원으로 알고 있음 -> 불일치)

```

<br>

## 4. 해결 옵션: clearAutomatically와 flushAutomatically

Spring Data JPA는 이 문제를 해결하기 위해 `@Modifying`에 두 가지 핵심 옵션을 제공한다.

### (1) `clearAutomatically = true` (기본값: false)

* **동작:** 쿼리 실행 직후, 영속성 컨텍스트를 깨끗하게 비운다.
* **효과:** 이후에 해당 엔티티를 다시 조회하면, 영속성 컨텍스트가 비어있으므로 강제로 DB에서 다시 조회한다.
* **장점:** 데이터 정합성을 100% 보장해서 가장 안전하다.
* **단점:** 다시 조회하는 비용(SELECT 쿼리 추가)이 발생하여 1차 캐시의 이점을 포기함. 그리고 이미 영속성 컨텍스트에 있는 트랜잭션 중인 변경사항이 clear되면 저장되지 않고 버려질 수 있다.

### (2) `flushAutomatically = true` (기본값: false)

* **동작:** 쿼리 실행 직전에, 영속성 컨텍스트에 쌓여있는 쓰기 지연 SQL 저장소의 변경사항을 DB에 반영(flush)한다.
* **필요한 이유:** 만약 `@Modifying` 쿼리 실행 전에 `member.setName("새이름")`과 같은 변경 감지 로직이 있었다면, 아직 DB에 반영되지 않은 상태이다. 이때 벌크 연산이 실행되면 순서가 꼬일 수 있기 때문에, 이를 방지하기 위해 벌크 연산 전에 강제로 동기화하는 옵션이다.
* **Hibernate의 특징:** Hibernate는 기본적으로 쿼리 실행 전 flush 모드가 `AUTO`로 설정되어 있어 겹치는 테이블이 있으면 알아서 flush를 해주기도 하지만, 명시적으로 제어할 때 유용하다.

<br>

## 5. 결론: 언제 어떻게 써야 하는가?

개발자는 상황에 따라 두 가지 전략 중 하나를 선택해야 한다.

### 전략 A: 안전 최우선

* **설정:** `@Modifying(clearAutomatically = true)` 사용.
* **흐름:** 업데이트 쿼리 실행 -> 영속성 컨텍스트 초기화 -> 다음 로직에서 필요 시 DB 다시 조회.
* **특징:** 데이터 불일치로 인한 동시성 이슈 가능성을 크게 줄일 수 있음

### 전략 B: 성능 최적화 

* **설정:** `@Modifying` (옵션 없이 기본값 사용).
* **흐름**
1. DB 업데이트 쿼리 실행 (`repository.update...`).
2. **반드시** 메모리에 있는 객체 값도 수동으로 변경 (`entity.set...`).


* **특징:** `SELECT` 쿼리를 아낄 수 있어 성능상 이점. 단, 개발자가 실수로 수동 동기화를 빼먹으면 문제(데이터 증발, 클라이언트 응답 불일치 등)가 발생할 수 있음.

### 요약

`@Modifying`은 Spring Data JPA에게 해당 @Query가 조회(SELECT)가 아닌 데이터 변경용 JPQL 또는 네이티브 쿼리임을 명시하여, 쿼리 실행 시 Query.executeUpdate()를 사용하도록 한다.
이 방식으로 실행되는 변경 쿼리는 영속성 컨텍스트에 관리 중인 엔티티 상태를 자동으로 동기화하지 않으며, 데이터베이스에 직접 반영된다.

그 결과, 영속성 컨텍스트에 이미 로딩된 엔티티와 데이터베이스 상태 간에 불일치가 발생할 수 있으므로, 개발자는 clearAutomatically 옵션을 통한 영속성 컨텍스트 초기화나 엔티티 값의 수동 동기화를 통해 데이터 정합성을 명시적으로 보장해야 한다.