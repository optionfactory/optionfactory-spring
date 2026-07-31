package net.optionfactory.spring.data.jpa.filtering;

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

public class PerPhaseTransactionListener extends AbstractTestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(PerPhaseTransactionListener.class);
    private static final String TX_STATUS_ATTRIBUTE = PerPhaseTransactionListener.class.getName() + ".TX_STATUS";
    private static final String TX_PHASE_ATTRIBUTE = PerPhaseTransactionListener.class.getName() + ".TX_PHASE";

    private static final Map<Class<?>, Boolean> beforeEachCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> afterEachCache = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        return 4000; // Takes the standard transaction listener slot
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        if (hasLifecycleMethod(testContext.getTestClass(), BeforeEach.class, beforeEachCache)) {
            beginTx(testContext, "@BeforeEach");
        }
    }

    @Override
    public void beforeTestExecution(TestContext testContext) {
        endTx(testContext, true);
        beginTx(testContext, "@Test");
    }

    @Override
    public void afterTestExecution(TestContext testContext) {
        final var testPassed = testContext.getTestException() == null;
        endTx(testContext, testPassed);
        if (hasLifecycleMethod(testContext.getTestClass(), AfterEach.class, afterEachCache)) {
            beginTx(testContext, "@AfterEach");
        }
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        endTx(testContext, false);
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

    private void endTx(TestContext testContext, boolean defaultToCommit) {
        final var status = (TransactionStatus) testContext.getAttribute(TX_STATUS_ATTRIBUTE);
        final var phase = (String) testContext.getAttribute(TX_PHASE_ATTRIBUTE);
        if (status != null && !status.isCompleted()) {
            final var txManager = testContext.getApplicationContext().getBean(PlatformTransactionManager.class);
            final var testName = getTestName(testContext);
            final var exception = testContext.getTestException();
            final var hasTestException = (exception != null);
            final var isRollbackOnly = status.isRollbackOnly();
            if (defaultToCommit && !hasTestException && !isRollbackOnly) {
                txManager.commit(status);
                log.info("[TX][COMMIT][{}] {}", phase, testName);
            } else {
                txManager.rollback(status);
                log.info("[TX][ROLLBACK][{}] {}: {}", phase, testName, getRollbackReason(hasTestException, exception, isRollbackOnly));
            }
        }
        testContext.removeAttribute(TX_STATUS_ATTRIBUTE);
        testContext.removeAttribute(TX_PHASE_ATTRIBUTE);
    }

    private String getRollbackReason(boolean hasTestException, Throwable exception, boolean isRollbackOnly) {
        if (hasTestException) {
            return "Uncaught exception: " + exception.getClass().getSimpleName();
        }
        return isRollbackOnly ? "Transaction marked rollback-only" : "Default rollback";
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
