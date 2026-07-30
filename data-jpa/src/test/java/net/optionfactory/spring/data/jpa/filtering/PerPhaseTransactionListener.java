package net.optionfactory.spring.data.jpa.filtering;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.transaction.TestTransaction;

public class PerPhaseTransactionListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        return 4001; // Runs right after TransactionalTestExecutionListener (4000)
    }

    @Override
    public void beforeTestExecution(TestContext testContext) {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();   // Commits & closes @BeforeEach's transaction (clears L1 cache)
            TestTransaction.start(); // Starts a fresh transaction for @Test
            TestTransaction.flagForCommit();
        }
    }
}