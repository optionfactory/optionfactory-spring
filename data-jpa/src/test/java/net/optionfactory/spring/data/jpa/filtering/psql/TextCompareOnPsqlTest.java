package net.optionfactory.spring.data.jpa.filtering.psql;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import net.optionfactory.spring.data.jpa.test.containers.SharedContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SharedContainer(HibernateOnPsqlTestConfig.Postgres.class)
@SpringJUnitConfig(HibernateOnPsqlTestConfig.class)
@TransactionalPhases
public class TextCompareOnPsqlTest {

    @Entity
    @TextCompare(name = "byName", path = "name")
    public static class Root {

        @Id
        public long id;
        public String name;

    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    private RootsRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        repo.save(root(1, "Alpha_Beta"));
        repo.save(root(2, "AlphaXBeta"));
        repo.save(root(3, "gamma"));
        repo.save(root(4, null));
    }

    private static Root root(long id, String name) {
        final var r = new Root();
        r.id = id;
        r.name = name;
        return r;
    }

    @Test
    public void containsIgnoreCaseMatchesCaseFolded() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "PH"))
                .build();
        final Page<Root> page = repo.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(2, page.getTotalElements());
    }

    @Test
    public void containsIgnoreCaseEscapesWildcards() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "A_B"))
                .build();
        final Page<Root> page = repo.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(1, page.getTotalElements());
        Assertions.assertEquals(1L, page.getContent().get(0).id);
    }

    @Test
    public void startsWithIgnoreCaseMatches() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.startsWith(CaseSensitivity.IGNORE_CASE, "alPhA"))
                .build();
        final Page<Root> page = repo.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(2, page.getTotalElements());
    }

    @Test
    public void endsWithIgnoreCaseEscapesWildcards() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.endsWith(CaseSensitivity.IGNORE_CASE, "_BETA"))
                .build();
        final Page<Root> page = repo.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(1, page.getTotalElements());
        Assertions.assertEquals(1L, page.getContent().get(0).id);
    }

    @Test
    public void containsCaseSensitiveStillMatches() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains(CaseSensitivity.CASE_SENSITIVE, "lpha"))
                .build();
        final Page<Root> page = repo.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(2, page.getTotalElements());
    }

    @Test
    public void containsCaseSensitiveDoesNotFoldCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains(CaseSensitivity.CASE_SENSITIVE, "LPHA"))
                .build();
        final Page<Root> page = repo.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(0, page.getTotalElements());
    }
}
