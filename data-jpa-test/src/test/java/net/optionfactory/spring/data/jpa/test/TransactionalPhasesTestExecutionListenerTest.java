package net.optionfactory.spring.data.jpa.test;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/// Runs the fixture classes below through the launcher against a recording `PlatformTransactionManager`
/// (no database), asserting which phases commit and which roll back.
public class TransactionalPhasesTestExecutionListenerTest {

    @Test
    public void everyPhaseCommitsWhenNothingFails() {
        Assertions.assertEquals(List.of("COMMIT @BeforeEach", "COMMIT @Test", "COMMIT @AfterEach"), run(Passing.class));
    }

    @Test
    public void failingTestRollsBackOnlyTheTestPhase() {
        Assertions.assertEquals(List.of("COMMIT @BeforeEach", "ROLLBACK @Test", "COMMIT @AfterEach"), run(FailingTest.class));
    }

    @Test
    public void failingTestAndAfterEachRollBackBothPhases() {
        Assertions.assertEquals(List.of("COMMIT @BeforeEach", "ROLLBACK @Test", "ROLLBACK @AfterEach"), run(FailingTestAndAfterEach.class));
    }

    private static List<String> run(Class<?> fixture) {
        RecordingTransactionManager.EVENTS.clear();
        Jupiter.run(fixture);
        return List.copyOf(RecordingTransactionManager.EVENTS);
    }

    /* fixtures */
    @SpringJUnitConfig(Config.class)
    @TransactionalPhases
    public static class Passing {

        @BeforeEach
        public void before() {
        }

        @Test
        public void test() {
        }

        @AfterEach
        public void after() {
        }
    }

    public static class FailingTest extends Passing {

        @Test
        @Override
        public void test() {
            Assertions.fail("test failure");
        }
    }

    public static class FailingTestAndAfterEach extends FailingTest {

        @AfterEach
        @Override
        public void after() {
            throw new IllegalStateException("after failure");
        }
    }

    @Configuration
    public static class Config {

        @Bean
        public PlatformTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    /// Records `COMMIT`/`ROLLBACK` per phase, the phase being taken from the transaction name set by the listener.
    public static class RecordingTransactionManager implements PlatformTransactionManager {

        static final List<String> EVENTS = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            final var name = definition.getName();
            return new SimpleTransactionStatus() {
                @Override
                public String toString() {
                    return name.substring(name.indexOf('(') + 1, name.indexOf(')'));
                }
            };
        }

        @Override
        public void commit(TransactionStatus status) {
            EVENTS.add("COMMIT " + status);
        }

        @Override
        public void rollback(TransactionStatus status) {
            EVENTS.add("ROLLBACK " + status);
        }
    }
}
