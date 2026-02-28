## 1. 개요

JPA(Jakarta Persistence API)의 연관관계는 객체 모델 간의 참조를 관계형 데이터베이스의 테이블 관계에 매핑하는 메커니즘이다.  
JPA는 관계형 모델에서 일반적으로 사용되는 네 가지 연관관계 유형(일대일, 일대다, 다대일, 다대다)을 지원하며, 각 관계는 단방향 또는 양방향으로 정의할 수 있다.


## 2. 연관관계 유형

### 2.1 One-to-One

- 한 엔티티 인스턴스가 다른 엔티티 인스턴스 정확히 하나와 연관되는 관계
- `@OneToOne` 사용, 외래키 위치에 따라 owner을 결정한다.

```java
@Entity
public class Customer {
    @Id
    @GeneratedValue
    private Long id;

    @OneToOne
    @JoinColumn(name = "shipping_address_id")
    private ShippingAddress shippingAddress;
}
```



### 2.2 One-to-Many / Many-to-One

- 하나의 엔티티가 여러 엔티티와 연관(One-to-Many), 여러 엔티티가 하나의 엔티티와 연관(Many-to-One)되는 관계
- Many-to-One/One-to-Many 양방향 관계에서 다(Many)측이 항상 owner이다.

```java
// Many-to-One (owner)
@Entity
public class Employee {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id") // 외래키
    private Department department;
}

// One-to-Many (mappedBy)
@Entity
public class Department {
    @Id @GeneratedValue
    private Long id;

    @OneToMany(mappedBy = "department")
    private List<Employee> employees = new ArrayList<>();
}
```



### 2.3 Many-to-Many

- 양쪽 엔티티 모두 여러 인스턴스와 상호 연관되는 관계  
- 중간 조인 테이블을 통해 구현되며, JPA에서는 `@ManyToMany`와 `@JoinTable`로 매핑한다.

```java
@Entity
public class Store {
    @Id @GeneratedValue
    private Long id;

    @ManyToMany
    @JoinTable(
        name = "store_product",
        joinColumns = @JoinColumn(name = "store_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> products = new HashSet<>();
}
```

Many-to-Many는 조인 테이블이 직접적으로 도메인 의미를 가질 경우(추가 속성이 있는 관계 등) 별도의 엔티티로 승격시키는 것이 권장된다.


## 3. 방향성 (단방향 vs 양방향)

### 3.1 단방향(Unidirectional)

- 한쪽 엔티티만 다른 엔티티를 참조하는 관계
- 객체 모델에서 한 방향으로만 탐색이 가능하며, 데이터베이스 스키마에는 영향을 주지 않는다.  
- 장점: 모델 단순화, 순환 참조 감소, 유지보수 비용 감소.

```java
@Entity
public class OrderItem {
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order; // Order → OrderItem 방향 없음
}
```



### 3.2 양방향(Bidirectional)

- 양쪽 엔티티가 서로를 참조하는 관
- 구성 요소
  - Owning side: 실제 외래키를 매핑하고 변경사항을 DB에 반영하는 측.
  - Inverse side: `mappedBy`를 통해 Owning side의 필드를 참조하며, DB 업데이트는 하지 않는다.

```java
@Entity
public class Order {
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items = new ArrayList<>();
}

@Entity
public class OrderItem {
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order; // Owning side
}
```

- 규칙 요약:
  - Many-to-One/One-to-Many: Many 측이 주인
  - One-to-One: 외래키가 있는 테이블에 해당하는 엔티티가 주인
  - Many-to-Many: 어느 한쪽을 임의로 주인으로 지정 가능

양방향 관계에서는 컬렉션/참조 양쪽을 일관되게 갱신하기 위한 헬퍼 메서드 사용이 권장된다.


## 4. 주요 매핑 어노테이션 및 속성

### 4.1 연관관계 어노테이션

주요 어노테이션의 기본 속성은 다음과 같다.

- `@ManyToOne`
  - 기본 `fetch = EAGER`
  - 기본 `optional = true`
- `@OneToMany`
  - 기본 `fetch = LAZY`
  - `mappedBy`로 양방향 관계에서 지정
  - `orphanRemoval` 지원
- `@OneToOne`
  - 기본 `fetch = EAGER`
  - `optional`, `orphanRemoval` 지원
- `@ManyToMany`
  - 기본 `fetch = LAZY`
  - 조인 테이블 매핑 시 `@JoinTable` 사용



### 4.2 CascadeType

CascadeType은 부모 엔티티에 수행된 작업을 연관된 자식 엔티티로 전파하는 기능이다.

지원되는 타입:

- `PERSIST` – 부모 persist 시 자식도 persist  
- `MERGE` – 부모 merge 시 자식도 merge  
- `REMOVE` – 부모 remove 시 자식도 remove  
- `REFRESH` – 부모 refresh 시 자식도 refresh  
- `DETACH` – 부모 detach 시 자식도 detach  
- `ALL` – 위 모든 타입 포함

```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItem> items = new ArrayList<>();
```

One-to-Many에서 `CascadeType.REMOVE`/`ALL` 사용 시, 의도치 않은 대량 삭제에 주의해야 한다.



### 4.3 FetchType (지연/즉시 로딩)

FetchType은 연관 엔티티 로딩 시점을 제어한다.

- `LAZY` (지연 로딩)
  - 실제 접근 시까지 로딩을 지연하며, 초기에는 프록시가 할당된다.
  - `@OneToMany`, `@ManyToMany`의 기본값.
- `EAGER` (즉시 로딩)
  - 주 엔티티 로딩 시 연관 엔티티를 함께 조회한다.
  - `@ManyToOne`, `@OneToOne`의 기본값.
  - N+1 문제 및 불필요 조인으로 인한 성능 저하를 유발할 수 있다.

대부분의 연관관계에 대해 LAZY를 명시적으로 사용하는 것이 권장된다.



### 4.4 orphanRemoval

`orphanRemoval = true`는 부모와의 관계가 끊어진 자식 엔티티를 자동으로 삭제한다.

```java
@OneToMany(mappedBy = "order", orphanRemoval = true)
private List<OrderItem> items;
```

비교: `CascadeType.REMOVE` vs `orphanRemoval`

- `CascadeType.REMOVE`: 부모 엔티티를 삭제할 때 자식도 같이 삭제
- `orphanRemoval = true`: 부모는 유지하되, 컬렉션/참조에서 제거된 자식만 DB에서 삭제

자식 엔티티의 생명주기가 부모에 완전히 종속된 경우(예: `Order` – `OrderItem`)에 사용하는 것이 적절하다.


## 5. 조인 매핑

### 5.1 @JoinColumn

`@JoinColumn`은 외래키 컬럼명을 지정하며, 연관관계의 주인에서 사용한다.

```java
@ManyToOne
@JoinColumn(name = "order_id", nullable = false)
private Order order;
```

기본값을 사용할 경우, 구현체는 관례에 따라 컬럼명을 생성한다(일반적으로 `<필드명>_<참조키>` 패턴)



### 5.2 @JoinTable

`@JoinTable`은 조인 테이블 기반 연관관계를 매핑하는 데 사용되며, 주로 Many-to-Many에 사용된다.

```java
@ManyToMany
@JoinTable(
    name = "store_product",
    joinColumns = @JoinColumn(name = "store_id"),
    inverseJoinColumns = @JoinColumn(name = "product_id")
)
private Set<Product> products;
```

조인 테이블에 추가 속성이 필요할 경우, 조인 테이블을 별도의 엔티티로 모델링하는 것이 권장된다.



## 6. 성능 이슈 및 최적화

### 6.1 N+1 문제

N+1 문제는 한 번의 쿼리로 N개의 엔티티를 조회한 뒤, 각 엔티티의 연관관계 로딩을 위해 추가로 N개의 쿼리가 발생하는 상황을 의미한다.

해결 방법

- JPQL Fetch Join 사용: `SELECT p FROM Product p JOIN FETCH p.stores`  
- EntityGraph 사용: 페치 전략을 런타임에서 선언적으로 지정  
- Batch Fetch 설정: Hibernate의 `default_batch_fetch_size` 등으로 지연 로딩 컬렉션/프록시에 대한 배치 로딩을 수행



### 6.2 설계시 고려 사항

- 불필요한 양방향 관계를 지양하고, 단방향 관계를 우선 고려한다.  
- 대용량 컬렉션에 대해서는 컬렉션 필드 대신 쿼리+페이징 접근을 사용하는 것이 바람직하다.  
- 양방향 관계에는 양쪽 연관 필드를 일관되게 갱신하는 헬퍼 메서드를 제공한다.



## 7. 기타 매핑

### 7.1 임베디드 타입 (@Embeddable/@Embedded)

임베디드 타입은 값 타입을 별도 클래스로 추출하여 재사용 및 응집도를 높인다.

```java
@Embeddable
public class Address {
    private String city;
    private String street;
}

@Entity
public class User {
    @Id @GeneratedValue
    private Long id;

    @Embedded
    private Address address;
}
```

동일 임베디드 타입을 여러 필드에 사용할 경우, `@AttributeOverrides`로 컬럼 명을 구분한다.



### 7.2 복합 기본키 (@EmbeddedId, @IdClass)

복합 기본키는 `@EmbeddedId` 또는 `@IdClass`로 매핑할 수 있다.


```java
@Embeddable
public class OrderItemId implements Serializable {
    private Long orderId;
    private Long itemId;
}

@Entity
public class OrderItem {
    @EmbeddedId
    private OrderItemId id;
}
```

두 방식 모두 equals/hashCode 구현과 직렬화 요건을 충족해야 하며, 일반적으로 객체지향적인 측면에서 `@EmbeddedId`가 선호된다.



### 7.3 상속 매핑

JPA는 세 가지 상속 전략을 제공한다.

- `SINGLE_TABLE` (기본): 전체 계층을 단일 테이블에 저장, Discriminator 컬럼 사용
- `JOINED`: 각 클래스마다 테이블을 분리하고 조인으로 결합, 스키마 정규화
- `TABLE_PER_CLASS`: 구체 클래스마다 독립 테이블, 성능 및 쿼리 복잡도 측면에서 일반적으로 비권장

전략 선택은 성능 요구사항, 스키마 정규화 수준, 직접 SQL 접근 여부 등을 고려하여 결정해야 한다.
