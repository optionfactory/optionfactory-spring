package net.optionfactory.spring.data.jpa.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestExecutionListeners;

/// Runs each phase of a test method (`@BeforeEach`, `@Test`, `@AfterEach`) in its own transaction,
/// replacing Spring's `@Transactional` test support. Every phase **commits** unless it throws or marks its
/// transaction rollback-only, so flushes, constraints and triggers surface as they would in production, and
/// each phase sees what the previous ones committed.
///
/// Requires a `PlatformTransactionManager` bean in the test `ApplicationContext`. Since committed data is
/// shared with other tests, generate unique identifiers (e.g. from a static `AtomicLong`) or clean up in
/// `@AfterEach` (which commits even when the test failed) rather than relying on rollback for isolation.
///
/// ```java
/// @SpringJUnitConfig(DatabaseConfig.class)
/// @TransactionalPhases
/// public class RepositoryTest {
///
///     @BeforeEach
///     public void setup() { /* committed: visible to the test in a new transaction */ }
///
///     @Test
///     public void test() { /* committed if it passes, so flush/constraint problems surface here */ }
///
///     @AfterEach
///     public void cleanup() { /* committed, even when the test failed */ }
/// }
/// ```
///
/// A phase can be rolled back on purpose by throwing, or by marking its transaction rollback-only: join it with a
/// `TransactionTemplate` and call `TransactionStatus#setRollbackOnly()` on the inner status, or let a
/// `@Transactional` bean under test throw (the interceptor marks the joined transaction even if the test catches
/// the exception). Spring's `@Rollback`/`@Commit` are not honoured.
///
/// @see TransactionalPhasesTestExecutionListener
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestExecutionListeners(
    listeners = TransactionalPhasesTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
public @interface TransactionalPhases {
}