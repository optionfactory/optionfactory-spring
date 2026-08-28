# optionfactory-spring/data-jpa-test

JUnit Jupiter support for JPA integration tests: containers shared across the whole test run, and per-phase transactions.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
```

Add the Testcontainers module you need (e.g. `org.testcontainers:testcontainers-postgresql`) alongside it.

## Usage

### Shared containers

A container declared with `@SharedContainer` is started at most once per JVM, lazily before the first annotated
test class runs, and stopped once after the last test has run (when the JUnit engine's root context closes).
Tests that don't declare it never pay for it. Ryuk remains as the safety net for crashed JVMs.

#### 1. Describe the container

```java
public class TestPostgres implements ContainerDefinition<PostgreSQLContainer> {

    @Override
    public PostgreSQLContainer start() throws Exception {
        final var image = DockerImageName.parse("optionfactory/debian13-postgres18:235").asCompatibleSubstituteFor("postgres");
        final var container = new PostgreSQLContainer(image)
                .withExposedPorts(5432)
                .withUsername("postgres")
                .withDatabaseName("test");
        container.start();
        // one-off initialization: the image ignores POSTGRES_* env, align the server with what the container reports
        container.execInContainer("psql", "-U", "postgres", "-c", "ALTER USER postgres PASSWORD 'test'");
        container.execInContainer("psql", "-U", "postgres", "-c", "CREATE DATABASE test");
        return container;
    }

    @Override
    public Map<String, Object> properties(PostgreSQLContainer container) {
        return Map.of(
                "db.jdbc.url", container.getJdbcUrl(),
                "db.username", container.getUsername(),
                "db.password", container.getPassword()
        );
    }
}
```

The implementation must be public with a public no-arg constructor: the class is the identity of the shared
instance, so every test declaring `@SharedContainer(TestPostgres.class)` shares the same running container.

#### 2. Declare it

Directly on a test class, or as a meta-annotation on a project specific composed annotation; repeat it for
each container the tests depend on:

```java
@SharedContainer(TestPostgres.class)
@SpringJUnitConfig(DatabaseConfig.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IntegrationTest {
}
```

With Spring, the definition's `properties()` are added to the test `ApplicationContext` environment with the same
precedence as `@DynamicPropertySource`, so `@Value("${db.jdbc.url}")` (or `spring.datasource.*`) just works.
Tests declaring different containers get different cached contexts.

Without Spring, or for direct access:

```java
final PostgreSQLContainer pg = SharedContainerRegistry.get(TestPostgres.class);
```

#### Caveats

* One container per forked JVM: with Surefire `forkCount > 1` each fork starts its own.
* Container properties take precedence over `@TestPropertySource`, as `@DynamicPropertySource` does.

### Per-phase transactions

`@TransactionalPhases` replaces Spring's `@Transactional` test support, running `@BeforeEach`, `@Test` and
`@AfterEach` each in its own transaction. Every phase **commits**, unless that phase throws or marks its
transaction rollback-only:

* `@BeforeEach` commits, so the test sees the fixture as production code would;
* `@Test` commits if it passes and rolls back if it throws;
* `@AfterEach` commits (even when the test failed), so cleanup performed there sticks.

```java
@SpringJUnitConfig(DatabaseConfig.class)
@TransactionalPhases
public class RepositoryTest {

    @BeforeEach
    public void setup() { /* committed: visible to the test in a new transaction */ }

    @Test
    public void test() { /* committed if it passes, so flush/constraint problems surface here */ }

    @AfterEach
    public void cleanup() { /* committed, even when the test failed */ }
}
```

Data committed by tests is shared: generate unique identifiers (a static `AtomicLong` works) or clean up in
`@AfterEach` rather than relying on rollback.

#### Marking a phase for rollback

A phase is rolled back when it throws or when its transaction is marked rollback-only. From test code:

* **Throw.** Any exception (a failed assertion included) rolls back the current phase; the following phases still
  run in their own transactions.
* **Participate and mark.** A `TransactionTemplate` with the default `PROPAGATION_REQUIRED` joins the phase
  transaction, so marking the inner status marks the whole phase:

  ```java
  @Test
  public void leavesNoTrace() {
      repo.save(entity);
      tt.executeWithoutResult(TransactionStatus::setRollbackOnly); // tt: an injected TransactionTemplate
  }
  ```
* **Let a `@Transactional` bean fail.** When a `@Transactional` method under test throws a runtime exception, the
  interceptor marks the joined phase transaction rollback-only, even if the test catches the exception (e.g. with
  `assertThrows`): the phase is then rolled back, and the log says so. Asserting on database state after such a call
  must happen in the next phase (`@AfterEach`) or in a new transaction.
* **From production code**, `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` works as usual
  inside a `@Transactional` method; it does not work in the test method itself, which is not proxied.

Spring's `@Rollback` and `@Commit` are not honoured: they belong to `TransactionalTestExecutionListener`, which
this listener replaces.
