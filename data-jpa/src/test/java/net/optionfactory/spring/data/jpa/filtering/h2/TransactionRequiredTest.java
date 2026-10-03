package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/// Repositories open no transaction of their own, and refuse to run outside one: reads would
/// otherwise run on a throwaway entity manager, and writes fail deep in the persistence layer.
@SpringJUnitConfig(HibernateOnH2TestConfig.class)
public class TransactionRequiredTest {

    @Entity
    public static class Ledger {

        @Id
        public long id;
        public String name;
    }

    public interface LedgerRepository extends JpaRepository<Ledger, Long>, WhitelistFilteringRepository<Ledger> {

        long countByName(String name);
    }

    @Inject
    private LedgerRepository repo;

    @Inject
    private PlatformTransactionManager transactionManager;

    @Test
    public void aReadOutsideATransactionIsRejected() {
        final var thrown = Assertions.assertThrows(IllegalTransactionStateException.class, () -> repo.count(), "a repository read outside a transaction is rejected");
        Assertions.assertTrue(thrown.getMessage().contains("LedgerRepository"), "the failure names the repository, got: " + thrown.getMessage());
    }

    @Test
    public void aDerivedQueryOutsideATransactionIsRejected() {
        Assertions.assertThrows(IllegalTransactionStateException.class, () -> repo.countByName("x"), "a derived query outside a transaction is rejected too");
    }

    @Test
    public void aWriteOutsideATransactionIsRejected() {
        final var ledger = new Ledger();
        ledger.id = 1;
        Assertions.assertThrows(IllegalTransactionStateException.class, () -> repo.save(ledger), "a repository write outside a transaction is rejected");
    }

    @Test
    public void insideATransactionRepositoriesWork() {
        final var tt = new TransactionTemplate(transactionManager);
        final var count = tt.execute(s -> {
            final var ledger = new Ledger();
            ledger.id = 2;
            repo.save(ledger);
            return repo.count();
        });
        Assertions.assertEquals(1L, count, "inside a transaction, reads and writes work");
        tt.executeWithoutResult(s -> repo.deleteAll());
    }

    @Test
    public void objectMethodsNeedNoTransaction() {
        Assertions.assertNotNull(repo.toString(), "toString, equals and hashCode are not repository operations");
    }
}
