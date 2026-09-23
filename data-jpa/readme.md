# optionfactory-spring/data-jpa

Declarative whitelisted filters and sorters on JPA `@Entity` types.

## Why

A filtering layer decides what a client is allowed to ask for. Most of them let the client
send a **path** — `firstName==john`, `?owner.city=roma` — which makes your entity graph the
query surface. Two consequences follow, and this library exists to avoid both.

**The filter name is the contract, not the path.** A request carries `byName`, never
`firstName`. The path lives on the entity and can change without a client noticing: rename a
property, move it into an embeddable, promote a `@ManyToOne` into a collection — that last one
changing the generated SQL from a join to a correlated `EXISTS` — and `byName` still means what
it meant. Saved filters, bookmarked URLs and integrations keep working. When the query surface
*is* the entity graph, none of those refactorings are free.

**The default is closed.** A name that isn't declared on the entity is rejected, and there is no
path for a client to walk. Securing a path-based filter means enumerating what must *not* be
reachable, which is unbounded — in a multi-tenant schema, `owner.organization.…` is one hop from
another tenant's rows. Here you enumerate what you do want. Whitelists, their paths and their
property types are all resolved when the repository is built, so a bad declaration fails at
startup rather than on the first request that happens to use it.

Neither property costs you expressiveness on the server side: a base `Specification` still
applies arbitrary conditions to every query (tenant scoping, soft deletes), and `@Filterable`
binds a custom filter that can build anything the Criteria API can — still behind a stable name.

## When not to use it

- **Clients need ad-hoc boolean composition.** A `FilterRequest` is a conjunction of named
  filters: no `OR` between them, no nesting, and a given filter appears at most once. That is the
  price of the closed contract, not an oversight. If clients must express `(a and b) or c` over
  your schema, you want a query language — RSQL or spring-filter — and the coupling it brings.
- **The bottleneck is the query, not the filtering.** Projections selecting only the columns a
  DTO needs, keyset pagination for deep pages, CTEs, window functions: Blaze-Persistence solves
  those, this does not.
- **Three scalar columns and no plans to grow.** A hand-written `Specification` is less machinery
  than an annotation set.

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

Every comparison annotation above also takes a `match` quantifier, which applies when — and only
when — its path crosses a collection, and which such a filter must state: see
[Filtering across a collection](#filtering-across-a-collection). `@TextSearch` and `@Sortable`
have none, as neither accepts a collection-crossing path.

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
| `@Sortable` | none | Whitelists a sortable path, resolved through the same traversal engine as filters |
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
  `EXISTS` subquery, keeping pagination safe from row multiplication. What the subquery
  asks of the elements is the filter's [quantifier](#filtering-across-a-collection).
- Nested collections are folded into the same subquery by default.

Override the defaults with `@FilterTraversal` on the entity:

```java
@Entity
@FilterTraversal(path = "address.state.city", joinType = JoinType.INNER)
@FilterTraversal(path = "departments.employees", reuse = false)
@TextCompare(name = "byStreet", path = "address.state.city.street")
public class Company { ... }
```

`@Sortable` paths go through the same engine, so a filter and a sorter on the same path
share one join and honour the same `@FilterTraversal` overrides. Sorting has one extra
restriction: an `ORDER BY` has to name an expression of the selected row, so a plural hop
cannot be folded into an `EXISTS` and would have to be joined from the root, multiplying
rows. Paths crossing a collection are therefore rejected when the repository is built,
with an `InvalidSortConfiguration` — as is any path that does not resolve against the
metamodel.

## Filtering across a collection

A filter whose path crosses a collection asks a question about the row's *elements*, and
that question has two independent halves: the **condition** (`label = "x"`) and the
**quantifier** — must *some* element satisfy it, or *none*? The quantifier is `match` on the
filter itself, because it is part of what the filter means, and whoever names the filter also
writes the label the user reads:

```java
@Entity
@TextCompare(name = "withTag", path = "tags.label", match = Match.ANY)
@TextCompare(name = "withoutTag", path = "tags.label", match = Match.NONE)
public class Pet { ... }
```

- `ANY` renders `EXISTS (...)`: the row is kept when at least one element satisfies the
  condition. A row with an empty collection is dropped — it has no element to satisfy
  anything. This is the reading every positive operator always had.
- `NONE` renders `NOT EXISTS (...)`: the row is kept when no element satisfies the condition.
  A row with an empty collection matches, correctly so.

Neither reading is a safe default, so there is none: `match` defaults to `Match.UNSTATED`, and a
filter whose path crosses a collection without stating `ANY` or `NONE` is rejected with an
`InvalidFilterConfiguration` when the repository is built. A filter whose path crosses no
collection needs no quantifier and can leave it out. Conversely, a quantifier needs something to
quantify over, so `match = NONE` on a path that crosses no collection is rejected too, rather than
ignored — otherwise the annotation would state the opposite of what the filter does.

**Do not express a negative filter as `ANY` over a negated operator.** `withTag NEQ "x"` asks
"is there a tag that isn't `x`?", so it keeps a pet tagged both `x` and `y`, and drops a pet
with no tags at all. What a UI labelled *without tag x* means is `withoutTag EQ "x"`. `NEQ`
remains available for the rare case where the existential reading is genuinely wanted; if it
is not, leave it out of the filter's whitelisted `operators`:

```java
@TextCompare(name = "withTag", path = "tags.label", operators = {Operator.EQ, Operator.CONTAINS})
```

`ANY` and `NONE` partition the rows over the same condition, which is what a filter widget
implies: given pets `EMPTY`, `HAS-X`, `HAS-X-AND-Y` and `HAS-Y`, `withTag EQ "x"` returns
`HAS-X, HAS-X-AND-Y` and `withoutTag EQ "x"` returns exactly the other two.

### Composition

Filters reaching the same collection with the same quantifier are folded into one subquery,
which describes **one element**: every condition must hold for the *same* element. Filters
that disagree on the quantifier describe different elements, so they simply get one subquery
each — there is nothing to reconcile and no configuration to keep in sync:

```java
// withTag=x, withWeight>5        -> EXISTS (label = x AND weight > 5)         one tag, both conditions
// withoutTag=x, withoutWeight>5  -> NOT EXISTS (label = x AND weight > 5)     no tag with both
// withTag=x, withoutTag=y        -> EXISTS (label = x) AND NOT EXISTS (label = y)
```

Use `@FilterTraversal(path = "...", reuse = false)` to split filters that share a quantifier
into separate subqueries, i.e. to ask for *an* element per filter rather than one element
satisfying all of them.

### Custom filters

`@Filterable` has no `path`, so it has no `match` either: a custom filter owns what it
traverses and therefore owns its quantifier. Implement `TraversalFilter` and state it on the
traversal, and the filter is folded, grouped and negated exactly like a built-in one. The
three-argument `Filters.traversal(entity, name, path)` states none, so it is rejected on a path
crossing a collection, exactly as an annotation that leaves `match` out:

```java
public class TagAbsenceFilter implements TraversalFilter<String> {

    private final Traversal traversal;

    public TagAbsenceFilter(Filterable annotation, EntityType<?> entity) {
        this.traversal = Filters.traversal(entity, annotation.name(), "tags.label", Match.NONE);
    }

    @Override
    public Predicate condition(Root<?> root, Path<String> path, CriteriaBuilder builder, String[] values) {
        return builder.equal(path, values[0]);
    }
    // name(), traversal()
}
```

A custom filter implementing `Filter` directly is handed the `CriteriaQuery` and builds
whatever subquery it wants, so nothing is imposed on it either way.

### What it costs

The collection is joined straight from the correlated row, so the root table is read once:

```sql
where exists(select 1 from tag t1_0 where t1_0.label = ? and p1_0.id = t1_0.pet_id)
```

A path that crosses a singular association *before* the collection (`kennel.badges.label`) is
the exception: an outer join cannot hang off a correlated row without a `FROM` entry for it,
so the root is joined again inside the subquery. Declare the singular hop as
`@FilterTraversal(path = "kennel", joinType = JoinType.INNER)` when that extra access matters
and the association is mandatory.

The join into the collection itself is always `INNER` — an outer join there would produce a
row for every parent and make the subquery vacuously true. A `joinType` on a plural hop is
therefore ignored, as it is on an embedded one.

## Streaming

Streaming `findAll` overloads return matched entities as a `Stream` instead of
materializing a list, hinting the JDBC fetch size and, optionally, loading
entities read-only:

```java
// pure mapping: read-only entities, detached right after the mapper returns;
// the stream bounds its own memory, no session management needed
try (Stream<PersonDto> dtos = persons.findAll(scope, fr, sort, 512, PersonDto::from)) {
    ...
}

// full control: the callback receives the SessionPolicy and picks the load mode
persons.findAll(scope, fr, sort, 512, SessionPolicy.Mode.READ_ONLY, (policy, person) -> {
    ...
    policy.clearIf(1000);
    return dto;
});
```

`Mode.DEFAULT` keeps today's semantics: entities are managed with a
dirty-check snapshot, so the callback may mutate them and rely on flush — the
mode to use whenever the stream is not a pure read. `Mode.READ_ONLY` sets
Hibernate's per-query read-only hint: no snapshot is kept, roughly halving
persistence-context memory per entity, and **mutations made by the callback
are silently ignored at flush** — which is why the mode is explicit rather
than inferred. The hint affects only the entities this query loads, never the
rest of the caller's transaction. Read-only entities still accumulate in the
persistence context as the stream advances: the mapping overloads detach each
entity right after mapping, while policy-based callbacks should evict with
`SessionPolicy.detaching` per row or bulk `clear()`/`clearIf(n)` — the cheaper
option on large scans.

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


## Indexing case-insensitive comparisons

`@TextCompare` with `IGNORE_CASE` renders two different shapes, and they want
different indexes. `CONTAINS`/`STARTS_WITH`/`ENDS_WITH` compile to a native
`ILIKE` where the dialect has one (postgres, h2), which a `pg_trgm` GIN index
serves; the remaining operators (`EQ`, `NEQ`, `LT`, `GT`, `LTE`, `GTE`,
`BETWEEN`) compare `lower(column)`, so a plain index on the column cannot serve
them — they need a functional index on the same expression:

```sql
-- postgres, @TextCompare(name = "byEmail", path = "email") used with IGNORE_CASE + EQ
CREATE INDEX by_email_lower_idx ON person (lower(email));

-- postgres, @TextCompare(name = "byName", path = "name") used with IGNORE_CASE + CONTAINS
CREATE INDEX by_name_trgm_idx ON person USING GIN (name gin_trgm_ops);
```

Case-sensitive `STARTS_WITH` is the one shape a plain btree can serve, and on
postgres only when the index is declared with `text_pattern_ops` (or the
database runs the `C` collation).
