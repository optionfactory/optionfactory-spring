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
| `@TextCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN, CONTAINS, STARTS_WITH, ENDS_WITH` | Plus a `CaseSensitivity` (`CASE_SENSITIVE`, `IGNORE_CASE`) argument. `IGNORE_CASE` `CONTAINS`/`STARTS_WITH`/`ENDS_WITH` render as a native `ILIKE` where the dialect supports it (e.g. postgres, h2), so plain pg_trgm indexes can serve them; other dialects fall back to `lower() ... like lower(...)` |
| `@NumberCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN` | Primitives (except `boolean`) and `Number` subtypes |
| `@InstantCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN` | `java.time.Instant`; configurable `format` (`ISO_8601`, `UNIX_S`, `UNIX_MS`, `UNIX_NS`) |
| `@LocalDateCompare` | `EQ, NEQ, LT, GT, LTE, GTE, BETWEEN` | `java.time.LocalDate` |
| `@BooleanCompare` | `EQ, NEQ` | Customizable `trueValue` / `falseValue` tokens |
| `@InEnum` | enum constants | Matches any of the given constants of `type` |
| `@InList` | values | Matches any of the given values |
| `@TextSearch` | free text | Full-text search over one or more text `paths` on postgres and mysql/mariadb (see below) |
| `@Sortable` | none | Whitelists a sortable path |
| `@Filterable` | none | Binds a custom `Filter` implementation |

### 3. Create a Repository

Extend `WhitelistFilteringRepository` (it composes with `JpaRepository`):

```java
public interface PersonRepository extends JpaRepository<Person, Long>, WhitelistFilteringRepository<Person> {
}
```

### 4. Use the Repository

Build a `FilterRequest`: the typed builder helpers produce correctly-ordered argument
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

## Full-Text Search (postgres, mysql/mariadb)

`@TextSearch` searches a document composed of one or more text `paths`. The
elements are engine-neutral; the rendering is per database: postgres renders
`to_tsvector(language, p1 || ' ' || p2) @@ <query>`, mysql and mariadb render
`MATCH(p1, p2) AGAINST(<query> IN BOOLEAN MODE)`. The engine is picked from
the Hibernate dialect at startup; anything else fails fast with
`InvalidFilterConfiguration`.

```java
@Entity
@TextSearch(name = "byContent", paths = {"title", "body"}, language = "english")
@TextSearch(name = "byFreeText", paths = {"title", "body"}, language = "english", syntax = TextSearch.Syntax.WEBSEARCH)
@TextSearch(name = "byTitle", paths = "title", language = "italian", syntax = TextSearch.Syntax.PHRASE)
public class Article { ... }
```

```java
FilterRequest fr = FilterRequest.builder()
    .textSearch("byContent", "cats running")
    .build();
```

- `PLAIN` (default): every term must match, no client syntax
- `WEBSEARCH`: `"quoted phrases"`, `OR` between terms, `-term` exclusion
- `PHRASE`: terms must appear adjacent, in order

These semantics hold on every engine; the *recall* does not: postgres stems
and drops stopwords per `language` (the regconfig, default `simple`), so
`cats` finds `cat`; mysql has no per-query linguistics and matches whole
words, case-folded by the column collation. Tokens shorter than
`innodb_ft_min_token_size` (3 by default) are invisible to mysql, and without
an explicit `Sort` mysql returns rows in relevance order rather than an
unspecified-but-stable one.

Paths may cross singular associations on postgres; mysql `MATCH()` needs
plain columns of the root table, so association crossings are rejected at
startup there, and at most 8 paths are supported (one `of_match_against_N`
rendering is registered per arity). Collection paths are rejected on every
engine: this is a deliberate, revisitable scope decision, not a technical
impossibility. The known design — grouping paths by collection prefix and
folding each group into a correlated `EXISTS` component inside the adapter's
subquery planner — trades semantics for capability: the document becomes a
disjunction of components, so a `PHRASE` (or all `PLAIN` terms) must occur
within a single component, and matching one term in the parent plus one in a
collection element would stop working. Until that trade is worth making,
search the child entity directly or use a `@Filterable` custom filter.

MariaDB dispatches through the same mysql engine (`MariaDBDialect` extends
`MySQLDialect`) and is expected to work, but the test suite exercises mysql 8
and postgres only.

Index pairing, per engine (the predicate is served by an index only when the
DDL matches what the filter renders):

```sql
-- postgres, @TextSearch(paths = {"title", "body"}, language = "english")
CREATE INDEX by_content_fts_idx ON article
    USING GIN (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(body, '')));

-- postgres, @TextSearch(paths = "title", language = "english")
CREATE INDEX by_title_fts_idx ON article
    USING GIN (to_tsvector('english', coalesce(title, '')));

-- mysql/mariadb, @TextSearch(paths = {"title", "body"})
CREATE FULLTEXT INDEX by_content_fts_idx ON article (title, body);
```

A missing postgres index degrades to a sequential scan; a missing mysql
fulltext index makes the query fail outright (`Can't find FULLTEXT index
matching the column list`). The library registers a `FunctionContributor`
(`TextSearchFunctions`) exposing the `@@` operator and `MATCH...AGAINST` to
criteria queries; it is additive and inert unless `@TextSearch` is used.

