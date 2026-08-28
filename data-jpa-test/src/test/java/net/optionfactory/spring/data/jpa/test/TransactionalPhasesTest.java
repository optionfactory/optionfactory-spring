package net.optionfactory.spring.data.jpa.test;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/// Against a real JPA setup (H2): each phase runs in its own transaction, so a phase can observe what the
/// previous phases committed. Tests are ordered: each one asserts on the markers left by the previous one.
@SpringJUnitConfig(H2JpaTestConfig.class)
@TransactionalPhases
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransactionalPhasesTest {

    @Entity
    public static class PhaseMarker {

        @Id
        public String id;
    }

    public interface PhaseMarkers extends JpaRepository<PhaseMarker, String> {
    }

    private final PhaseMarkers markers;
    private final TransactionTemplate tt;

    @Inject
    public TransactionalPhasesTest(PhaseMarkers markers, TransactionTemplate tt) {
        this.markers = markers;
        this.tt = tt;
    }

    @BeforeEach
    public void setup(TestInfo info) {
        mark("before", info);
    }

    @AfterEach
    public void teardown(TestInfo info) {
        mark("after", info);
    }

    @Test
    @Order(1)
    public void beforeEachIsCommittedAndRollbackOnlyTestIsRolledBack(TestInfo info) {
        Assertions.assertTrue(markers.existsById("before:" + name(info)), "@BeforeEach must be committed before @Test");
        mark("test", info);
        /* participating in the @Test transaction: marks it rollback-only */
        tt.executeWithoutResult(TransactionStatus::setRollbackOnly);
    }

    @Test
    @Order(2)
    public void afterEachIsCommittedEvenWhenTestIsRolledBack(TestInfo info) {
        Assertions.assertAll(
                () -> Assertions.assertFalse(markers.existsById("test:beforeEachIsCommittedAndRollbackOnlyTestIsRolledBack"), "rollback-only @Test must be rolled back"),
                () -> Assertions.assertTrue(markers.existsById("after:beforeEachIsCommittedAndRollbackOnlyTestIsRolledBack"), "@AfterEach must be committed regardless of the @Test outcome")
        );
        mark("test", info);
    }

    @Test
    @Order(3)
    public void passingTestAndAfterEachAreCommitted() {
        Assertions.assertAll(
                () -> Assertions.assertTrue(markers.existsById("test:afterEachIsCommittedEvenWhenTestIsRolledBack"), "passing @Test must be committed"),
                () -> Assertions.assertTrue(markers.existsById("after:afterEachIsCommittedEvenWhenTestIsRolledBack"), "@AfterEach must be committed")
        );
    }

    private void mark(String phase, TestInfo info) {
        final var marker = new PhaseMarker();
        marker.id = phase + ":" + name(info);
        markers.save(marker);
    }

    private static String name(TestInfo info) {
        return info.getTestMethod().orElseThrow().getName();
    }
}
