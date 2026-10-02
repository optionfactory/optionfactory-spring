# 0016. Integration tests run against the real database, committing per phase

Status: accepted

## Context

Persistence code tested against an in-memory database, inside a transaction
rolled back at the end of the test, misses what production hits: dialect
specific SQL, constraints checked at flush or commit, and data that a fixture
wrote but never committed.

## Decision

`data-jpa-test` provides the infrastructure, and the library's own tests use
it:

- `@SharedContainer` starts a database container once per JVM, lazily, and
  shares it across test classes and spring contexts.
- `@TransactionalPhases` runs `@BeforeEach`, the test and `@AfterEach` each in
  its own committing transaction, so the test sees the fixture as production
  code would, and flush or constraint problems surface in the phase that
  caused them.

Dialect-specific behaviour is tested on postgres and mysql; the rest runs on
H2.

## Consequences

- Tests clean up after themselves explicitly; there is no rollback to rely
  on.
- Container tests need docker, and are slower than the rest; they are kept to
  what depends on the dialect.
- Assertions are plain JUnit, with a message stating the expected behaviour.
