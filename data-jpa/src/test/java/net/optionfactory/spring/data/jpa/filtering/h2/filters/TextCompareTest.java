package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class TextCompareTest {

    @Entity
    @TextCompare(name = "byName", path = "name")
    @TextCompare(name = "byDesc", path = "description")
    @TextCompare(name = "byTitle", path = "title")
    public static class Root {

        @Id
        public long id;
        public String name;
        public String description;
        public String title;

    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    private RootsRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        final Root a = new Root();
        a.id = 123;
        a.name = "asd";
        a.description = "test";
        a.title = null;
        repo.save(a);
    }

    @Test
    public void textCompareEquals() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.eq("asd"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "EQ matches the row with the exact name");
    }

    @Test
    public void textCompareBetweenCaseSensitive() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.between(CaseSensitivity.CASE_SENSITIVE, "a", "z"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "case-sensitive BETWEEN includes a name within the bounds");
    }

    @Test
    public void textCompareBetweenIgnoreCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.between(CaseSensitivity.IGNORE_CASE, "A", "Z"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "case-insensitive BETWEEN includes a lower-case name within upper-case bounds");
    }

    @Test
    public void textCompareEqualsIgnoreCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.eq(CaseSensitivity.IGNORE_CASE, "ASD"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "case-insensitive EQ matches a name differing only in case");
    }

    @Test
    public void textCompareContains() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains("s"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "CONTAINS matches a name containing the value");
    }

    @Test
    public void textCompareContainsIgnoreCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "S"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "case-insensitive CONTAINS matches a name containing the value in another case");
    }

    @Test
    public void textCompareStartsWith() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.startsWith("a"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "STARTS_WITH matches a name starting with the value");
    }

    @Test
    public void textCompareStartsWithIgnoreCase() {

        final var fr = FilterRequest.builder()
                .text("byName", f -> f.startsWith(CaseSensitivity.IGNORE_CASE, "A"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "case-insensitive STARTS_WITH matches a name starting with the value in another case");
    }

    @Test
    public void textCompareEndsWith() {

        final var fr = FilterRequest.builder()
                .text("byName", f -> f.endsWith("d"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "ENDS_WITH matches a name ending with the value");
    }

    @Test
    public void textCompareEndsWithIgnoreCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.endsWith(CaseSensitivity.IGNORE_CASE, "D"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(123L, page.getContent().get(0).id, "case-insensitive ENDS_WITH matches a name ending with the value in another case");
    }

    @Test
    public void filteringWithNeqIncludesNullValues() {

        final var fr = FilterRequest.builder()
                .text("byTitle", f -> f.neq(CaseSensitivity.IGNORE_CASE, "D"))
                .build();

        final Page<Root> all = repo.findAll(Pageable.unpaged());
        final Page<Root> page = repo.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(all.getTotalElements(), page.getTotalElements(), "NEQ keeps the row whose value is null");
    }
}
