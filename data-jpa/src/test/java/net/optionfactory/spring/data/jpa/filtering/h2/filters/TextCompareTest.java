package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/// Each operator is checked against rows it must match and rows it must not: names differing in
/// case, containing the value elsewhere, or `NULL`.
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
        repo.save(root(1, "asd", null));
        repo.save(root(2, "ASD", "d"));
        repo.save(root(3, "bsx", "x"));
        repo.save(root(4, "xas", "x"));
        repo.save(root(5, null, "x"));
    }

    @Test
    public void textCompareEquals() {
        Assertions.assertEquals(Set.of(1L), ids("byName", f -> f.eq("asd")), "EQ matches only the row with the exact name");
    }

    @Test
    public void textCompareBetweenCaseSensitive() {
        Assertions.assertEquals(Set.of(1L), ids("byName", f -> f.between(CaseSensitivity.CASE_SENSITIVE, "a", "b")), "case-sensitive BETWEEN includes only the names within the bounds, excluding upper-case ones");
    }

    @Test
    public void textCompareBetweenIgnoreCase() {
        Assertions.assertEquals(Set.of(1L, 2L), ids("byName", f -> f.between(CaseSensitivity.IGNORE_CASE, "A", "B")), "case-insensitive BETWEEN includes the names within the bounds in any case");
    }

    @Test
    public void textCompareEqualsIgnoreCase() {
        Assertions.assertEquals(Set.of(1L, 2L), ids("byName", f -> f.eq(CaseSensitivity.IGNORE_CASE, "ASD")), "case-insensitive EQ matches the names differing only in case");
    }

    @Test
    public void textCompareContains() {
        Assertions.assertEquals(Set.of(1L, 3L, 4L), ids("byName", f -> f.contains("s")), "CONTAINS matches the names containing the value anywhere, in the same case");
    }

    @Test
    public void textCompareContainsIgnoreCase() {
        Assertions.assertEquals(Set.of(1L, 2L, 3L, 4L), ids("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "S")), "case-insensitive CONTAINS matches the names containing the value in any case");
    }

    @Test
    public void textCompareStartsWith() {
        Assertions.assertEquals(Set.of(1L), ids("byName", f -> f.startsWith("a")), "STARTS_WITH matches only the names starting with the value, in the same case");
    }

    @Test
    public void textCompareStartsWithIgnoreCase() {
        Assertions.assertEquals(Set.of(1L, 2L), ids("byName", f -> f.startsWith(CaseSensitivity.IGNORE_CASE, "A")), "case-insensitive STARTS_WITH matches the names starting with the value in any case");
    }

    @Test
    public void textCompareEndsWith() {
        Assertions.assertEquals(Set.of(1L), ids("byName", f -> f.endsWith("d")), "ENDS_WITH matches only the names ending with the value, in the same case");
    }

    @Test
    public void textCompareEndsWithIgnoreCase() {
        Assertions.assertEquals(Set.of(1L, 2L), ids("byName", f -> f.endsWith(CaseSensitivity.IGNORE_CASE, "D")), "case-insensitive ENDS_WITH matches the names ending with the value in any case");
    }

    @Test
    public void filteringWithNeqIncludesNullValues() {
        Assertions.assertEquals(Set.of(1L, 3L, 4L, 5L), ids("byTitle", f -> f.neq(CaseSensitivity.IGNORE_CASE, "D")), "NEQ drops only the matching title, and keeps the row whose title is null");
    }

    private Set<Long> ids(String filter, Function<TextCompare.Filter, String[]> values) {
        final var fr = FilterRequest.builder().text(filter, values).build();
        return repo.findAll(null, fr, Pageable.unpaged()).stream().map(r -> r.id).collect(Collectors.toSet());
    }

    private static Root root(long id, String name, String title) {
        final Root r = new Root();
        r.id = id;
        r.name = name;
        r.description = "test";
        r.title = title;
        return r;
    }
}
