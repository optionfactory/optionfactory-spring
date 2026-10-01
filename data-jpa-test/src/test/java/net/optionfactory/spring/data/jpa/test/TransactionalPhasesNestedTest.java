package net.optionfactory.spring.data.jpa.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// JUnit executes the enclosing instance's lifecycle methods for `@Nested` classes: the phase
/// transactions must be opened for them too.
@SpringJUnitConfig(H2JpaTestConfig.class)
@TransactionalPhases
public class TransactionalPhasesNestedTest {

    private boolean outerBeforeEachSawTransaction;

    @BeforeEach
    public void outerSetup() {
        outerBeforeEachSawTransaction = TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Nested
    public class Inner {

        @Test
        public void outerBeforeEachRunsInAPhaseTransaction() {
            Assertions.assertTrue(outerBeforeEachSawTransaction,
                    "the enclosing instance's @BeforeEach must run inside the phase transaction");
        }

    }

}
