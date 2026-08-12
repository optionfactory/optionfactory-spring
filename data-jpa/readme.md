# optionfactory-spring/data-jpa

Declarative whitelisted filters on JPA `@Entity` types.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>data-jpa</artifactId>
</dependency>
```

## Usage

### 1. Enable Filtering Repositories

Use `@EnableJpaWhitelistFilteringRepositories` instead of `@EnableJpaRepositories`:

```java
@Configuration
@EnableJpaWhitelistFilteringRepositories(basePackageClasses = MyRepository.class)
public class JpaConfig {
}
```

### 2. Annotate the Entity

Filters are declared as repeatable annotations on the entity. Each annotation binds a
**filter name** (the key clients use to activate the filter) to a **property path**
(navigated via the JPA metamodel):

```java
@Entity
@TextCompare(name = "byName", path = "name")
@TextCompare(name = "byEmail", path = "email", operators = { TextCompare.Operator.EQ, TextCompare.Operator.CONTAINS })
@NumberCompare(name = "byAge", path = "age", operators = { NumberCompare.Operator.GTE })
@BooleanCompare(name = "byActive", path = "active")
@InEnum(name = "byStatus", path = "status", type = Person.Status.class)
@Sortable(name = "sortByName", path = "name")
public class Person {
    @Id private long id;
    private String name;
    private String email;
    private int age;
    private boolean active;
    @Enumerated(EnumType.STRING) private Status status;

    public enum Status { ACTIVE, INACTIVE; }
}
```

Available built-in annotations:

| Annotation | Operators / Args | Notes |
|---|---|---|
| `@TextCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN, CONTAINS, STARTS_WITH, ENDS_WITH` | Plus a `CaseSensitivity` (`CASE_SENSITIVE`, `IGNORE_CASE`) argument |
| `@NumberCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN` | Primitives (except `boolean`) and `Number` subtypes |
| `@InstantCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN` | `java.time.Instant`; configurable `format` (`ISO_8601`, `UNIX_S`, `UNIX_MS`, `UNIX_NS`) |
| `@LocalDateCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN` | `java.time.LocalDate` |
| `@BooleanCompare` | `EQ, NEQ` | Customizable `trueValue` / `falseValue` tokens |
| `@InEnum` | enum constants | Matches any of the given constants of `type` |
| `@InList` | values | Matches any of the given values |
| `@Sortable` | — | Whitelists a sortable path |
| `@Filterable` | — | Binds a custom `Filter` implementation |

### 3. Create a Repository

Extend `WhitelistFilteringRepository` (it composes with `JpaRepository`):

```java
public interface PersonRepository extends JpaRepository<Person, Long>, WhitelistFilteringRepository<Person> {
}
```

### 4. Use the Repository

Build a `FilterRequest` — the typed builder helpers produce correctly-ordered argument
arrays for each filter kind:

```java
FilterRequest fr = FilterRequest.builder()
    .text("byName", f -> f.eq(TextCompare.CaseSensitivity.IGNORE_CASE, "john"))
    .number("byAge", f -> f.gte(18))
    .bool("byActive", f -> f.eq(true))
    .inEnum("byStatus", Person.Status.ACTIVE)
    .build();

Page<Person> results = persons.findAll(fr, Pageable.ofSize(20));
```

All finders accept an optional base `Specification<T>` for conditions that must always
apply (e.g. tenant scoping):

```java
persons.findAll(tenantScope, fr, Pageable.ofSize(20));
```

## Relational Paths

Property paths may cross associations (`performer.name`, `address.state.city`). The
engine inspects the JPA metamodel segment-by-segment and deduces the execution plan
automatically, with no manual join configuration:

- **Singular** associations (`@ManyToOne`, `@OneToOne`) are navigated via an inline `LEFT JOIN`.
- **Plural** associations (`@OneToMany`, `@ManyToMany`) are evaluated inside a correlated
  `EXISTS` subquery, keeping pagination safe from row multiplication.
- Nested collections are folded into the same subquery by default.

Override the defaults with `@FilterTraversal` on the entity:

```java
@Entity
@FilterTraversal(path = "address.state.city", joinType = JoinType.INNER)
@FilterTraversal(path = "departments.employees", reuse = false)
@TextCompare(name = "byStreet", path = "address.state.city.street")
public class Company { ... }
```

