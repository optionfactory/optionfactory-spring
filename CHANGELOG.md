# version 27.14

## `data-jpa`

*   [ENH] **Rejected filter and sort requests say what was rejected, in terms safe to show the
    client.** `InvalidFilterRequest` and `InvalidSortRequest` expose `filter`/`sorter` — the name the
    client sent — and `reason`, phrased in terms of the client's request. Their message still names the
    entity (`in filter byDate@Pet: …`), which is useful in a log but is exactly what the name-based
    contract keeps private, so anything answering a client should use the fields rather than the
    message. See `data-jpa-web`'s `DataJpaProblemsModule`.
*   [FIX] **A join-type conflict between two declarations is a configuration error, not a bad
    request.** `Filters.path` reported two traversals disagreeing on the join type of the same hop as an
    `InvalidFilterRequest`, i.e. as the client's fault, with a reason naming the entity's attribute. No
    request can fix it, so it is now an `InvalidFilterConfiguration`. Only custom filters passing
    hand-built traversals could ever reach it: join types of the built-in filters come from
    `@FilterTraversal`, resolved per path, and cannot disagree.
*   [BREAKING] **Streaming `findAll` now requires an explicit `SessionPolicy.Mode`.** The
    `findAll(base, filters, sort, fetchSize, BiFunction)` overloads are gone: policy-based streaming
    call sites must state `SessionPolicy.Mode.DEFAULT` (managed, mutable entities, dirty-checked —
    today's behavior) or `SessionPolicy.Mode.READ_ONLY` explicitly, so the cheaper mode is a choice
    rather than a discovery problem.
*   [NEW] **Opt-in read-only streaming.** `Mode.READ_ONLY` sets Hibernate's per-query read-only hint
    on the streaming query: no dirty-check snapshot is kept (roughly halving persistence-context
    memory per entity) and mutations made by the callback are silently ignored at flush. The hint
    scopes to the entities this query loads, never the rest of the caller's transaction, and
    entities still accumulate until evicted — `SessionPolicy.detaching` per row or bulk
    `clear()`/`clearIf(n)` remain the tools for that. A `Function`-based shorthand
    (`findAll(base, filters, sort, fetchSize, mapper)`) compiles to `READ_ONLY` plus automatic
    detach right after mapping, so pure entity-to-DTO streams need no session management at all;
    the mapper must not retain the entity for lazy access after returning.

*   [FIX] **A malformed filter value is now always an `InvalidFilterRequest`.** The library was
    meticulous about whitelisting names and careless about what it threw when a whitelisted filter got
    a value it could not parse, letting the underlying parser's exception escape. Two of those are not
    even `IllegalArgumentException`s — `DateTimeParseException` and `StringIndexOutOfBoundsException` —
    so an application mapping by exception type answered a malformed date with a server error rather
    than a bad request, from the very component whose job is validating client input. Every conversion
    of a request value now reports `InvalidFilterRequest`, naming the filter, the value and the target
    type: `@LocalDateCompare` and `@InstantCompare` (all four formats) on unparseable text,
    `Values.convert` on any numeric conversion, and a `char` property on a value that is not exactly
    one character (previously an empty value threw `StringIndexOutOfBoundsException` from
    `charAt(0)`). `@TextCompare` parsed its operator through `Filters.parseEnum` and its case
    sensitivity through a raw `valueOf` one line below, so an unknown mode produced `No enum constant`
    instead of the library's own message; both now go through the same path. The typed `FilterRequest`
    builders take a `LocalDate`, an `Instant` or a `Number` and so were never able to express these
    values: they arrive from the wire, which is where the new tests put them.
*   [BREAKING] **Filtering across a collection is now quantified, and the `EXISTS` is genuinely
    correlated.** A filter path crossing a collection asks something about the row's elements, which is
    two independent questions: the condition, and whether *some* element must satisfy it or *none*.
    Only the first was expressible, so negation was written as `ANY` over a negated operator — and the
    empty-collection case was patched by outer-joining inside the subquery, which made a row with no
    elements match whenever that particular operator's SQL happened to tolerate `NULL`. The result
    depended on the operator rather than on the filter: `tags.label NEQ 'x'` returned rows with no tags
    *and* rows having both an `x` and a `y` tag, and `tags.label EQ null` — "has an element whose label
    is null" — returned rows with no elements at all, which is simply a wrong row.
    Every path-based filter annotation now takes `match`: `Match.ANY` (the default, `EXISTS`) keeps a
    row when at least one element satisfies the condition, `Match.NONE` (`NOT EXISTS`) keeps it when
    none does. A filter meaning "without a tag x" is declared `match = NONE` over the condition `EQ x`,
    never `ANY` over `NEQ x`, and the two partition the rows the way a filter widget implies. The
    quantifier is part of a subquery group's identity: filters sharing one are folded as before (one
    element must satisfy every condition), filters disagreeing on it get a subquery each, so there is
    nothing to reconcile. `Match` lives on the filter, not on `@FilterTraversal`, because the same
    collection routinely carries both a "with" and a "without" filter; `@TextSearch` and `@Sortable`
    have no quantifier, neither accepting a collection-crossing path. `match = NONE` on a path without
    a collection is rejected when the repository is built, rather than ignored: with nothing to
    quantify over, the annotation would state the opposite of what the filter does.
    **This changes results silently, at runtime — nothing fails to compile.** Rows whose collection is
    empty no longer match an `ANY` filter, whatever the operator. Audit every filter whose `path`
    crosses a collection and is used with `NEQ` or a null value: those are the call sites that were
    relying on the old shape, and `match = NONE` over the positive condition is almost certainly what
    they meant. Filters over collections used only with positive operators are unaffected.
    The subquery now correlates the row instead of selecting the root table a second time
    (`exists(select 1 from tag t where t.pet_id = p.id and ...)`), so the root is read once. A path
    crossing a singular association before the collection still joins the root inside the subquery,
    since an outer join cannot hang off a correlated row; declare that hop
    `@FilterTraversal(joinType = INNER)` to avoid it. The join into the collection is always `INNER`,
    an outer join there being vacuously true, so a `joinType` on a plural hop is now ignored — as it
    already was on an embedded one.
*   [ENH] **An unfiltered `FilterRequest` no longer emits `1=1`.** `WhitelistFilteringSpecificationAdapter`
    returned `builder.and()` over zero predicates, which renders as a `1=1` restriction; it now returns
    `null`, the `Specification` contract's "unrestricted", so the where clause is dropped entirely and a
    filtered finder called with no filters emits exactly what the plain `JpaRepository` finder does. SPI
    callers invoking the adapter directly must accept a `null` predicate (`CriteriaQuery.where(null)` is
    fine; a hand-rolled `builder.and(mine, theirs)` is not).
*   [ENH] **The emitted predicates no longer depend on map iteration order.** Requested filters are now
    visited in filter-name order and subquery groups are held in a `TreeMap`, so the same logical request
    always renders the same SQL text. Isolated subquery groups (`@FilterTraversal(reuse = false)`) are
    keyed by path and filter name instead of a `UUID` minted at startup: the token is still unique per
    filter, but it is now stable across restarts and across nodes, so a database can keep reusing the
    cached plan for those queries. `AND` is commutative, so no result changes.
*   [ENH] **Clearer join-consistency diagnostics.** `Filters.step` checks the joins it reuses in a plain
    loop rather than a `peek` inside a stream, and the resulting `InvalidFilterRequest` now names both the
    existing and the requested join type. The check stays at request time: join types are resolved per
    path from `@FilterTraversal`, so two whitelisted filters sharing a prefix cannot disagree — only a
    custom `Filter` passing a hand-built `Traversal` to `Filters.path` can, and that is not visible when
    the repository is built.
*   [DOC] **Indexing case-insensitive comparisons.** The readme now spells out that `IGNORE_CASE` with
    `EQ`/`NEQ`/ranges/`BETWEEN` compares `lower(column)` and needs a functional index on that expression,
    while `CONTAINS`/`STARTS_WITH`/`ENDS_WITH` render as `ILIKE` and are served by a `pg_trgm` GIN index.
*   [BREAKING] **`@Sortable` paths are now resolved through the filters' traversal engine, and
    collection-crossing paths are rejected at startup.** Sorting used to hand the whitelisted path
    straight to spring's `QueryUtils`, which walks it with its own join logic: none of the traversal
    rules that keep filtering safe applied. A `@Sortable` whose path crossed a collection was
    accepted silently and rendered as a join from the root, multiplying rows — and since the `LIMIT`
    then counts joined rows while the count query counts entities, pages came back short, some
    entities repeated across pages and others never appeared at all (measured on 20 parents with 3
    children each, pages of 10: 15 distinct entities over 3 pages, 5 of them twice). An `ORDER BY`
    has to name an expression of the selected row, so a plural hop cannot be folded into an `EXISTS`
    the way a filter's is: such paths are now rejected with `InvalidSortConfiguration` when the
    repository is built. Sort the child entity, or map an aggregate, instead. Singular paths are
    navigated with `Filters.path` like a filter's, so a filter and a sorter on the same association
    provably share one join and `@FilterTraversal` overrides apply to both. `@Sortable` paths also
    get the startup validation filters always had: an unresolvable path fails when the repository is
    built rather than on the first request that uses that sorter. `Sort.Order`'s `ignoreCase` and
    null handling keep working as before. Path syntax is now the metamodel dot-notation filters use,
    so spring `PropertyPath` spellings that were never valid for a filter (e.g. camel-cased
    `ownerName` for `owner.name`) now fail at startup. One shape gets a join it did not have:
    `@Sortable(path = "owner.id")` now renders `left join owner ... order by owner.id` where spring
    used the `owner_id` foreign key column already on the root table — matching what a filter on the
    same path has always emitted. SPI: `Repositories.allowedSorters` returns
    `Map<String, Filters.Traversal>` instead of `Map<String, String>`, and
    `Sorters.validateAndTransform` is replaced by `Sorters.traversal` (startup) and
    `Sorters.orders`/`Sorters.order` (request time).

*   [NEW] **`@TextSearch`: full-text search filter for postgres and mysql/mariadb.** Searches a document
    composed of one or more text `paths` with a client-facing `syntax` and a document `language`.
    `PLAIN` (default) requires every term, no client syntax; `WEBSEARCH` opt-in exposes `"quoted
    phrases"`, `OR` and `-term`; `PHRASE` requires terms adjacent and in order. Those semantics hold
    on every engine; recall does not: postgres normalizes per `language` (the regconfig, so `cats`
    finds `cat`), mysql matches whole words case-folded by collation. Rendering is engine-specific:
    postgres emits `to_tsvector(lang, coalesce(p1,'') || ' ' || coalesce(p2,'')) @@ <query>` (paired
    with a GIN expression index over exactly that expression, since `concat_ws` is unindexable and
    the planner only matches the `@@` operator, not its function form); mysql/mariadb emit
    `MATCH(p1, p2) AGAINST(<query> IN BOOLEAN MODE)` (paired with a `FULLTEXT` index on the same
    columns in the same order; without one the query errors). The engine is chosen from the dialect
    at filter construction via the injected `EntityManagerFactory`: unsupported dialects fail fast
    with `InvalidFilterConfiguration`, and on mysql paths crossing associations are rejected there
    too, since `MATCH()` needs root-table columns, as are path lists longer than 8 (one rendering is
    registered per arity). Collection-crossing paths are rejected at startup on every engine: a
    deliberate, revisitable scope decision — the known design folds each collection group into a
    correlated `EXISTS` component in the adapter, making the document a disjunction of components
    (all terms must match within one component); until that trade is wanted, search the child entity
    or use a `@Filterable` custom filter. Queries are bound as inlined, quote-escaped literals; every
    mysql term is double-quoted so client text can never inject boolean operators. MariaDB dispatches
    through the same engine but is not covered by the test suite, which exercises postgres 18 and
    mysql 8.
    The module's `FunctionContributor` (`TextSearchFunctions`) registers `of_ts_matches` (`@@`) and
    `of_match_against_N` (`MATCH...AGAINST`); additive and inert unless used.
*   [ENH] **Case-insensitive `CONTAINS`/`STARTS_WITH`/`ENDS_WITH` now render as a native `ILIKE`.** `@TextCompare`
    with `IGNORE_CASE` used to compile to `lower(column) like '%value%'`, forcing a per-row `lower()` on the column
    and requiring a functional index on `lower(column)` to be servable. The predicate is now built as a
    case-insensitive Hibernate `ilike`, translated at render time by the dialect: postgres and h2 emit
    `column ilike ? escape '\'`, so plain pg_trgm GIN indexes become usable, while dialects without native
    case-insensitive matching keep the `lower(column) like lower(?)` emulation. `LIKE` wildcard escaping (`%`,
    `_`, `\`) is preserved in every mode, and `IGNORE_CASE` comparison operators (`EQ`, `NEQ`, ranges, `BETWEEN`)
    still compile to `lower()` comparisons.

## `problems-web`

*   [ENH] **`RestExceptionResolver` is assembled from modules, its own cases included.** The resolver
    answered spring mvc's, bean validation's, spring security's exceptions and `Failure`s from one
    `switch`; each group is now a built-in `ProblemsModule` — `SpringWebProblemsModule`,
    `BeanValidationProblemsModule`, `FailureProblemsModule`, `SpringSecurityProblemsModule` — consulted
    after every classifier an application or library registers, as the defaults: specific before general,
    as with `catch` clauses. A registered classifier can therefore refine a built-in case — answering a
    subclass of `Failure`, `RestClientException`, `ResponseStatusException` or `AccessDeniedException`
    its own way, which built-ins consulted first made impossible — and for the same reason must decline
    every exception it does not own. Built-in modules contribute transformers as well as classifiers,
    registered after the application's; detail omission still runs last. The resolver is left with what only it can do: consulting the
    classifiers, falling back to spring's defaults and reporting unexpected errors, running the
    transformers and rendering. Answers are unchanged, pinned by a test per built-in case written before
    the move and passing on both sides of it. Two things observably differ: each client error is logged by
    one `DEBUG` line, `Classified failure at <uri>: <problems>`, instead of a case-specific one, and a
    failed upstream call is logged at `WARN` by `SpringWebProblemsModule`'s logger instead of the
    resolver's — worth knowing if you filter logs by logger name. The groups follow the dependency each
    one needs, so that spring security and bean validation can later become optional.
    `ExceptionClassifier.annotatedStatusOr(ex, fallback)` exposes the `@ResponseStatus` lookup the
    built-in cases use, for library classifiers to honour it on their own exceptions.
*   [NEW] **`ExceptionClassifier` and `ProblemsModule`: let another library's exceptions be answered as
    what they are.** An exception the `RestExceptionResolver` has no case for is logged at `ERROR` with a
    stack trace and answered `500`. That is right for a bug, and wrong for a library whose exceptions
    describe a bad request — and the only way around it used to be a dependency between this module and
    that library, in one direction or the other. An `ExceptionClassifier` answers such an exception with a
    status and its problems; a library bundles its classifiers and transformers into a `ProblemsModule`,
    and the application registers it once, with `RestExceptionResolver.Builder#withModule`, so neither
    module depends on the other and the library can later contribute more without the application
    changing its configuration. Classifiers are consulted in registration order, the first not declining
    answering, and before the resolver falls back to reporting an unexpected error; a classified
    exception is logged at `DEBUG`. A classifier receives an `ExceptionClassifier.Context` — the request, response
    and handler, plus the resolver's message source and locale — so it can localize; a parameter object
    rather than a list of parameters, so it can grow without breaking classifiers already written. A
    module contributes classifiers and transformers and nothing else, so it cannot reconfigure the
    resolver: in particular it cannot include details in production, since detail omission runs after
    every transformer, a module's included. `FailureTransformer` could not do a classifier's job: it runs
    after the resolver has classified the exception, by which point an unknown one has already been
    logged as an error.
*   [BREAKING] **`RestExceptionResolver`'s constructor takes the classifiers.** The three-argument
    constructor is replaced by a four-argument one, with the classifiers ahead of the transformers.
    Applications building the resolver through `RestExceptionResolver.builder()` or `ExceptionResolvers`
    are unaffected.

## `data-jpa-web`

*   [NEW] **`DataJpaProblemsModule`: a rejected filter or sort request is a `400`.** A malformed filter
    value, an operator outside the whitelist or an unknown filter or sorter name used to reach
    `problems-web` as an `InvalidDataAccessApiUsageException` — spring's JPA exception translation
    rewraps every `IllegalArgumentException` leaving a repository — for which the rest resolver has no
    case: it was logged at `ERROR` and answered `500`. Registered with
    `rest.withModule(new DataJpaProblemsModule())`, its `FilteringExceptionClassifier` finds the rejection
    in the cause chain and answers `400` with a `FIELD_ERROR` problem whose `context` is the filter or
    sorter name and whose `reason` is phrased in terms of the client's request, so neither reveals the
    entity behind the name; the full message goes in `details`, omitted in production. Registering the
    module rather than the classifier means whatever this library contributes to `problems-web` later
    arrives without a configuration change. `problems-web` is an optional dependency: applications not
    using it are unaffected and never load these classes. `jakarta.servlet-api` moves from `test` to
    `provided` scope, as compiling against the classifier SPI needs it.

# version 27.12

## `problems-web`

*   [NEW] **`UndeliverableResponseExceptionResolver`:** declines, ahead of every other resolver, when the client has
    gone or the response is already committed, so the resolvers behind it are never asked to write a response
    that no longer exists. Register it with `ExceptionResolvers.configurer(...).undeliverables()`. A streaming
    endpoint commits its response with its first byte and normally ends because the client navigated away, and
    until now that produced two stack traces per disconnect: `RestExceptionResolver` logged the disconnect as an
    unexpected error and rendered a json problem document, then failed to write it, and the second failure
    escaped `render` where no resolver can catch it and surfaced as a tomcat `ERROR`. A plain `@Controller`
    fared worse, `PagesExceptionResolver` having no applicability test at all and answering a
    `text/event-stream` with an html error page. Disconnects are recognised through spring's
    `DisconnectedClientHelper`, which walks the cause chain and excludes `DataAccessException` and
    `RestClientException`, so a broken pipe to an upstream of ours still reports loudly; a genuine fault on an
    already committed response is still logged at `WARN`, since it cannot be told to the client but is still
    worth knowing.

# version 27.10

## `authentication`

*   [FIX] **An anonymous request no longer needs a principal mapping.** `AuthenticationsCoalescingFilter`
    threw `IllegalStateException: unmappable principal 'anonymousUser'` for spring's anonymous
    authentication unless the application registered a mapping for it, so adding
    `Principals.coalescing(...)` broke every request to a `permitAll` endpoint until that was
    discovered. An anonymous request carries no identity to normalise and is now left as spring made
    it. Registering a mapping for it still works, for applications that do want a guest principal of
    their own type, and an *authenticated* principal that nothing maps is still an
    `IllegalStateException`. That remains a misconfiguration worth failing on.

## `authentication-resource-server`

*   [DOC] **`JwtTokenResolverAdapter` now says what it is for.** It existed undocumented, and the
    reason it exists is not obvious: `BearerTokenAuthenticationFilter` claims every bearer token its
    resolver returns, hands it to the configured `JwtDecoder` and fails the request when the decoder
    cannot verify it, without checking whether another mechanism already authenticated the caller. So
    a resource server sharing the `Authorization` header with `authentication-tokens` rejects the
    static integration tokens and locally signed jws that its neighbour has just accepted. Removing
    the resolver from one application here failed 6 of its 9 authentication tests with
    `JwtDecoderInitializationException`. The class docs now carry that, the caveat that the predicate
    reads an unverified header and must therefore route rather than decide, and a usage example.
    `searchToken` is documented too.

## `authentication-tokens`

*   [FIX] **A schemeless header now matches.** `HeaderAndScheme` composed its matcher as
    `authScheme.toUpperCase().trim() + " "`, so a blank scheme produced `" "` and the filter looked for
    a header value starting with a space -- which a header carrying a bare token never has. Blank
    schemes are now normalised to `""`, for which the filter's existing `startsWith` / `substring`
    logic was already correct. Use `matchHeaderWithoutScheme(header)` on the JWS/JWE configurers, or
    `HeaderAndScheme.schemeless(header)` directly. Motivating case: a frontend that sends a jwt in a
    bare `jwt-auth` header, which previously needed a request-wrapping filter to translate.
*   [FIX] **`HeaderAndScheme` normalises in its constructor**, so every construction path is correct
    rather than only the builders that remembered to normalise. This also fixes direct construction
    with an un-suffixed scheme -- `new HeaderAndScheme("Authorization", "Bearer")` used to match
    nothing, because the matcher compares against an upper-cased value and expects the trailing
    space. Normalisation is idempotent, so already-correct values are unaffected.

# version 27.9

## `upstream-interceptor-jws`

*   [NEW] **`UpstreamJwtBearerGrantAuthenticator`:** signs an assertion (`iss`/`sub`/`aud`/`iat`/`exp`/`jti`/`scope`
    claims, `typ: JWT` header, RS256 by default), exchanges it for an access token at the token endpoint via the
    `upstream` module's `OauthClient`, and attaches the token as the `Authorization: Bearer` header value. The
    access token is cached thread-safely and refreshed 60 seconds (configurable) before the `expires_in` declared
    by the token endpoint. `sub` defaults to `iss` (service accounts) and is overridable for domain-wide
    delegation. This is the flow Google uses for service accounts.
*   [BREAKING] **`UpstreamJwsAuthenticator` accepts asymmetric signers:** the `byte[]`-secret constructor has
    been replaced by a `JWSSigner`-based one (HMAC users pass a `new MACSigner(secret)`), enabling
    RS256/RS384/RS512 and ES256 signatures; a `builder(...)` is also available, and the produced JWTs now carry
    a `typ: JWT` header.

## `upstream`

*   [NEW] **`OauthClient.jwtBearer(assertion)`:** exchanges a signed assertion for an access token using the
    RFC 7523 `urn:ietf:params:oauth:grant-type:jwt-bearer` grant, alongside the existing client_credentials,
    password and authorization_code grants.
*   [ENH] **`StreamHttpMessageConverter`:** the unreachable write path now throws a descriptive
    `UnsupportedOperationException` ("only supports reading, `canWrite()` is permanently false") instead of the
    autogenerated "Not supported yet.".

## `thymeleaf`

*   [NEW] **`VersionedResourceDialect`:** cache-busting attribute processor that appends a version query
    parameter to `href`/`src` attributes so browsers do not serve stale resources across deployments. Register
    it with `engine.addDialect(new VersionedResourceDialect(() -> version))` and annotate the tag with
    `version:append` (or the HTML5-friendly `data-version-append`); `/resource.js` renders as
    `/resource.js?version=xxxx`. An existing query string is extended with `&`, a pre-existing `version`
    parameter is never overridden, and empty attributes are left untouched.

## `upstream-alerts-email`

*   [BREAKING] [NEW] **Alert emails grouped by `EmailMessage.Prototype`:** `AlertsEmailsSpooler` now takes a
    `Function<String, EmailMessage.Prototype>` (resolved from `invocation().endpoint().upstream()`) instead of a
    single fixed prototype, so each integration's failures can be mailed to its own owners instead of everyone
    receiving every alert. The existing behavior is still available as `builder(...).bufferedScheduled(prototype)`
    (every alert to one prototype), alongside the new `bufferedScheduled(prototypeByUpstream)`. The selector must
    be stable (return the same instance per upstream, since prototypes are grouped by identity) and total (a `null`
    or throwing selector drops only its own alert, logged at WARN, not the whole batch).
*   [BREAKING] **Layout template independent of alert template location:** use the new
    `AlertsEmailsSpooler.templateEngine(prefix, messageSource, dialects...)` factory to build the engine: it
    resolves the shipped `alert-layout.html` (also available as `AlertsEmailsSpooler.LAYOUT`) from this module
    while resolving the application's alert templates from any prefix, so alert templates may now live anywhere
    in the classpath and still include `~{alert-layout.html :: alerts(...)}`.

## `client-reports`

*   [BUG] **Unparseable reports no longer fail the request:** `ClientReportFilter.bodyToJson` caught only
    `IOException`, but Jackson 3 signals malformed JSON with the unchecked `JacksonException`, so an unparseable
    (or over-truncated) client error report escaped the filter and produced a server error instead of the
    intended "unparseable report" fallback event. The fallback path is now covered by tests.

## `upstream-interceptor-jws` (test diagnostics)

*   [TEST] **Restored the missing `log4j-slf4j2-impl` test dependency:** test logging was silently disabled and
    `log4j2-test.properties` ignored.

## Test coverage

*   [TEST] **`authentication-tokens`:** header/scheme selector enforcement for JWS/JWE processors (tokens valid
    for one processor must not authenticate via a header/scheme that processor did not select), closing a
    long-standing TODO.
*   [TEST] **New suites** for `upstream-interceptor-spring-oauth2`, `client-reports`,
    `authentication-resource-server`, `thymeleaf` (`Money`, `SingletonDialect`) and `context`
    (`@ApplicationProperties`).

# version 27.8

## Dependencies and build

*   [DEP] **Bumped dependencies:** jsoup 1.23.2, libphonenumber 9.0.38, openhtmltopdf (core and pdfbox)
    1.1.83, classgraph 4.8.194.
*   [DEP] **Pinned Maven plugin versions:** core build plugins (clean, jar, surefire 3.5.6, site, resources,
    compiler 3.15.0, source, gpg, javadoc, central-publishing) are now pinned in the parent `pluginManagement`
    for reproducible builds.

# version 27.7

## `data-jpa-test`

*   [NEW] **New test-scoped module for JPA integration tests**, extracted from `data-jpa`'s own test sources:
    *   [NEW] **`@SharedContainer` / `ContainerDefinition`:** Testcontainers shared by every test in the JVM,
        started lazily before the first test class declaring them and stopped once after the last test (JUnit
        root store close). With Spring, the definition's `properties()` are exposed to the test `Environment`
        with `@DynamicPropertySource` precedence via an auto-registered `ContextCustomizerFactory`.
    *   [BREAKING] **`@TransactionalPhases` / `TransactionalPhasesTestExecutionListener`:** moved from `data-jpa`
        test sources, where they were `@PerMethodTransactional` / `PerPhaseTransactionListener` in package
        `net.optionfactory.spring.data.jpa.filtering`, to `net.optionfactory.spring.data.jpa.test`. Runs
        `@BeforeEach`, `@Test` and `@AfterEach` in separate transactions, each committing unless that phase
        throws or marks the transaction rollback-only. Behaviour change: the `@AfterEach` phase used to always
        roll back; it now commits, so cleanup performed there sticks.

# version 27.0

## `data-jpa`: whitelist filtering optimization

This release introduces a massive architectural refinement of the query compilation engine. The framework now
uses the **JPA Metamodel** as a single source of truth to auto-deduce database joining and subquery strategies
dynamically, completely eliminating the need for manual, verbose, string-based filter relationship decorations.

*   [BREAKING] **Removed `@FilterGroup`:** the `@FilterGroup` annotations (`@FilterGroup.Join` and
    `@FilterGroup.Subselect`) have been removed.
*   [BREAKING] **Removed startup join validation:** because relationship paths and subquery boundaries are now
    evaluated and adapted dynamically at runtime based on the JPA Metamodel, the startup check
    `Repositories.validateJoinTypes` has been entirely removed.
*   [ENH] **JPA metamodel-driven auto-deduction (zero-configuration defaults):** the engine inspects the
    structural type of your entity relationships segment-by-segment as it parses dot-notated filter paths:
    *   Singular relationships (`@ManyToOne`, `@OneToOne`): paths crossing singular attributes automatically
        default to an inline `JoinType.LEFT`, so parent entities are not inadvertently dropped from search
        results during negation (`NEQ`) or nullability filtering.
    *   Plural relationships (`@OneToMany`, `@ManyToMany`): paths crossing collection boundaries automatically
        group themselves and execute via an optimized `EXISTS` subquery, protecting queries from
        row-multiplication side effects and keeping Spring Data JPA pagination safe on collections.
    *   Safe structural fallbacks: paths targeting non-association fields (embeddables, records, or Postgres
        JSON columns mapped via `@JdbcTypeCode(SqlTypes.JSON)`) fall back to clean dot-notated object paths
        with zero injected SQL joins.
*   [ENH] **Automatic nested subquery folding:** when traversing deeply nested collections (e.g.
    `departments.employees.name`), the engine defaults to subquery folding (`reuse = true`), merging the nested
    collection predicate inside the parent's ongoing `EXISTS` context. This translates to a semantically unified
    constraint check (e.g. *"find a company that has an IT department containing an employee named John"*)
    without requiring any manual mapping annotations.
*   [NEW] **`@FilterTraversal`** for fine-tuning index paths or segregating complex nested relationship contexts,
    applied directly at the entity root. For most use cases, filtering behaves exactly as intended out of the
    box with zero extra annotations.

    ```java
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(FilterTraversal.List.class)
    public @interface FilterTraversal {
        String path();
        JoinType joinType() default JoinType.LEFT;
        boolean reuse() default true;
    }
    ```

    *   Performance tuning (join customization): path evaluation occurs step-by-step, so for a deep path like
        `"address.state.city.street"` you can force a strict `INNER JOIN` on one specific hop to optimize
        database index alignment, while preceding and trailing hops safely inherit default `LEFT JOIN` rules.

        ```java
        @Entity
        // Overriding the exact graph hop we want to index optimize
        @FilterTraversal(path = "address.state.city", joinType = JoinType.INNER)
        @TextCompare(name = "byStreet", path = "address.state.city.street")
        public class User {
            // ...
        }
        ```

    *   Disabling context folding (isolating subqueries): if you filter across multiple constraints on a
        collection and want them evaluated independently rather than unified together, flip `reuse = false` on
        that path. This splits the relational context, assigning a random `UUID` identifier under the hood to
        force the query adapter to compile a completely standalone, isolated `EXISTS` block.

        ```java
        @Entity
        // Forces each filter on 'leaves' to execute in a standalone, isolated EXISTS clause
        @FilterTraversal(path = "leaves", reuse = false)
        @BooleanCompare(name = "flag1", path = "leaves.flag1")
        @BooleanCompare(name = "flag2", path = "leaves.flag2")
        public class RootEntityWithSubselectFilters {
            // ...
        }
        ```

        * When `reuse = true` (default): generates `WHERE EXISTS (SELECT 1 ... WHERE flag1 = true AND flag2 = true)`.
          This finds root records where a single relation matches both conditions.
        * When `reuse = false`: generates `WHERE EXISTS (SELECT 1 ... WHERE flag1 = true) AND EXISTS (SELECT 1 ... WHERE flag2 = true)`.
          This finds root records where any arbitrary collection items satisfy the filters separately.

# version 26.0

## `email`

*   [BREAKING] **`EmailcheckServerIdentity`** now has a `checkServerIdentity` (defaulting to true).

## `downstream-maven-plugin`

*   [NEW] **Per-class `outputStyle` override:** can now be overridden by configuring `outputStyleOverrides`.

## `upstream-alerts-email`

*   [NEW] **New alert email template:** used email templates can be simplified.

## `data-jpa`

*   [ENH] **Subquery planner:** the filtering engine now acts as a query planner. Multiple filters assigned to a
    reusable subselect context are automatically collected and collapsed into a single optimized
    `EXISTS (SELECT ...)` SQL expression rather than spawning individual independent subqueries. Path navigation
    now maps against entity-declared `@FilterGroup` parameters using a "longest match wins" logic.
*   [BREAKING] **Removed/simplified `QueryMode` and `TraversalType`:** removed legacy internal structures;
    relational strategies are now fully declared via `@FilterGroup` configuration mappings and managed by the
    adapter planner. `TraversalFilter` has been completely stripped of query execution orchestration logic,
    leaving implementations to focus 100% on compiling criteria predicates.
*   [BREAKING] **Annotation configuration:** the `mode()` element has been deleted across all built-in
    whitelist filtering annotations. Classes explicitly specifying a query routing mode on the filter itself
    must remove it and replace it with `@FilterGroup`s:

    ```java
    // old (will not compile)
    @BooleanCompare(name = "flag", path = "leaves.flag", mode = QueryMode.SUBSELECT)
    ```

    ```java
    // new (relational layout is declared once via FilterGroups)
    @FilterGroup.Subselect(prefix = "leaves", reuse = true)
    @BooleanCompare(name = "flag", path = "leaves.flag")
    ```

*   [BREAKING] **`LowercaseUnderscoreSeparatedPhysicalNamingStrategy` removed:** use Hibernate's
    `CamelCaseToUnderscoreNamingStrategy`.
