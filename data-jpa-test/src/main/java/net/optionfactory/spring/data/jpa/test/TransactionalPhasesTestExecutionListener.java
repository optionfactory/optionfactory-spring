package net.optionfactory.spring.data.jpa.test;

import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.ReflectionUtils;

/// `TestExecutionListener` behind [TransactionalPhases]: brackets each phase of a test method in a
/// `PROPAGATION_REQUIRES_NEW` transaction obtained from the context's `PlatformTransactionManager`.
/// Every phase **commits**, unless that phase threw or marked its transaction rollback-only.
///
/// | Phase         | Begun in               | Ended in               | Rolled back when                                 |
/// |---------------|------------------------|------------------------|--------------------------------------------------|
/// | `@BeforeEach` | `beforeTestMethod`     | `beforeTestExecution`  | marked rollback-only                             |
/// | `@Test`       | `beforeTestExecution`  | `afterTestExecution`   | the test threw, or marked rollback-only          |
/// | `@AfterEach`  | `afterTestExecution`   | `afterTestMethod`      | an `@AfterEach` threw, or marked rollback-only   |
///
/// A failing test does not affect the `@AfterEach` transaction: cleanup performed there is committed. If a
/// `@BeforeEach` throws, JUnit skips the test but still runs the `@AfterEach` methods: they then execute in
/// the still open `@BeforeEach` transaction, which is rolled back.
///
/// The `@BeforeEach` and `@AfterEach` transactions are only opened when the test class (or a superclass)
/// declares a method with the corresponding annotation. Registered at order `4000`, the slot of Spring's
/// `TransactionalTestExecutionListener`, which it is meant to replace.
public class TransactionalPhasesTestExecutionListener extends AbstractTestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionalPhasesTestExecutionListener.class);
    private static final String TX_STATUS_ATTRIBUTE = TransactionalPhasesTestExecutionListener.class.getName() + ".TX_STATUS";
    private static final String TX_PHASE_ATTRIBUTE = TransactionalPhasesTestExecutionListener.class.getName() + ".TX_PHASE";
    private static final String TEST_EXCEPTION_ATTRIBUTE = TransactionalPhasesTestExecutionListener.class.getName() + ".TEST_EXCEPTION";
    private static final String TEST_SUPPRESSED_ATTRIBUTE = TransactionalPhasesTestExecutionListener.class.getName() + ".TEST_SUPPRESSED";

    private static final Map<Class<?>, Boolean> beforeEachCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> afterEachCache = new ConcurrentHashMap<>();

    /// @return `4000`, taking the standard transaction listener slot
    @Override
    public int getOrder() {
        return 4000;
    }

    /// Begins the `@BeforeEach` transaction, if the test class declares any `@BeforeEach` method.
    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (hasLifecycleMethod(testContext.getTestClass(), BeforeEach.class, beforeEachCache)) {
            beginTx(testContext, "@BeforeEach");
        }
    }

    /// Commits the `@BeforeEach` transaction (if any) and begins the `@Test` one.
    @Override
    public void beforeTestExecution(TestContext testContext) {
        endTx(testContext, null);
        beginTx(testContext, "@Test");
    }

    /// Commits the `@Test` transaction if the test passed (rolls back otherwise), then begins the
    /// `@AfterEach` transaction, if the test class declares any `@AfterEach` method.
    @Override
    public void afterTestExecution(TestContext testContext) {
        final var testException = testContext.getTestException();
        endTx(testContext, testException);
        if (hasLifecycleMethod(testContext.getTestClass(), AfterEach.class, afterEachCache)) {
            testContext.setAttribute(TEST_EXCEPTION_ATTRIBUTE, testException);
            testContext.setAttribute(TEST_SUPPRESSED_ATTRIBUTE, testException == null ? 0 : testException.getSuppressed().length);
            beginTx(testContext, "@AfterEach");
        }
    }

    /// Commits the `@AfterEach` transaction (if any), unless an `@AfterEach` method threw.
    @Override
    public void afterTestMethod(TestContext testContext) {
        endTx(testContext, afterEachException(testContext));
        testContext.removeAttribute(TEST_EXCEPTION_ATTRIBUTE);
        testContext.removeAttribute(TEST_SUPPRESSED_ATTRIBUTE);
    }

    /// The exception thrown by an `@AfterEach` method, if any. `TestContext.getTestException()` is JUnit's
    /// execution exception: the test's own exception, or the `@AfterEach` one when the test passed; when both
    /// threw, JUnit attaches the `@AfterEach` exception as a suppressed exception of the test's.
    private Throwable afterEachException(TestContext testContext) {
        final var exception = testContext.getTestException();
        if (exception == null) {
            return null;
        }
        final var testException = (Throwable) testContext.getAttribute(TEST_EXCEPTION_ATTRIBUTE);
        if (exception != testException) {
            return exception;
        }
        final var suppressedBefore = (Integer) testContext.getAttribute(TEST_SUPPRESSED_ATTRIBUTE);
        final var suppressed = exception.getSuppressed();
        return suppressedBefore != null && suppressed.length > suppressedBefore ? suppressed[suppressed.length - 1] : null;
    }

    private void beginTx(TestContext testContext, String phase) {
        final var txManager = testContext.getApplicationContext().getBean(PlatformTransactionManager.class);
        final var txDef = new DefaultTransactionDefinition();
        txDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        txDef.setName(getTestName(testContext) + " (" + phase + ")");
        log.info("[TX][BEGIN][{}] {}", phase, getTestName(testContext));
        final var status = txManager.getTransaction(txDef);
        testContext.setAttribute(TX_STATUS_ATTRIBUTE, status);
        testContext.setAttribute(TX_PHASE_ATTRIBUTE, phase);
    }

    /// Ends the current transaction: commits it unless `phaseException` is not null or the transaction is
    /// marked rollback-only.
    private void endTx(TestContext testContext, Throwable phaseException) {
        final var status = (TransactionStatus) testContext.getAttribute(TX_STATUS_ATTRIBUTE);
        final var phase = (String) testContext.getAttribute(TX_PHASE_ATTRIBUTE);
        if (status != null && !status.isCompleted()) {
            final var txManager = testContext.getApplicationContext().getBean(PlatformTransactionManager.class);
            final var testName = getTestName(testContext);
            if (phaseException == null && !status.isRollbackOnly()) {
                txManager.commit(status);
                log.info("[TX][COMMIT][{}] {}", phase, testName);
            } else {
                txManager.rollback(status);
                log.info("[TX][ROLLBACK][{}] {}: {}", phase, testName, getRollbackReason(phaseException));
            }
        }
        testContext.removeAttribute(TX_STATUS_ATTRIBUTE);
        testContext.removeAttribute(TX_PHASE_ATTRIBUTE);
    }

    private String getRollbackReason(Throwable phaseException) {
        return phaseException != null
                ? "Uncaught exception: " + phaseException.getClass().getSimpleName()
                : "Transaction marked rollback-only";
    }

    private String getTestName(TestContext testContext) {
        return testContext.getTestClass().getSimpleName() + "." + testContext.getTestMethod().getName();
    }

    private boolean hasLifecycleMethod(Class<?> testClass, Class<? extends Annotation> annotationType, Map<Class<?>, Boolean> cache) {
        return cache.computeIfAbsent(testClass, clazz -> hasLifecycleMethod(clazz, annotationType));
    }

    private boolean hasLifecycleMethod(Class<?> clazz, Class<? extends Annotation> annotationType) {
        for (Method method : ReflectionUtils.getAllDeclaredMethods(clazz)) {
            if (AnnotatedElementUtils.isAnnotated(method, annotationType)) {
                return true;
            }
        }
        return false;
    }
}
