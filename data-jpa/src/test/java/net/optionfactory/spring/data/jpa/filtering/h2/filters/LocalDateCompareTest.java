package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class LocalDateCompareTest {

    @Entity
    @LocalDateCompare(name = "date", path = "date")
    public static class Root {

        @Id
        public long id;
        public LocalDate date;
    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    private RootsRepository repo;

    @BeforeEach
    public void setup() {
        repo.saveAll(Arrays.asList(
                entity(1, LocalDate.parse("2019-01-10")),
                entity(2, LocalDate.parse("2019-01-11")),
                entity(3, LocalDate.parse("2019-01-11")),
                entity(4, LocalDate.parse("2019-02-25")),
                entity(5, LocalDate.parse("2019-10-01")),
                entity(6, null)
        ));
    }

    @Test
    public void canFilterByLocalDateEquality() {
        final Page<Root> page = repo.findAll(null, filter(LocalDateCompare.Operator.EQ, "2019-01-11"), Pageable.unpaged());
        Assertions.assertEquals(Set.of(2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateLessThan() {
        final Page<Root> page = repo.findAll(null, filter(LocalDateCompare.Operator.LT, "2019-01-11"), Pageable.unpaged());
        Assertions.assertEquals(Set.of(1L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateGreaterThan() {
        final Page<Root> page = repo.findAll(null, filter(LocalDateCompare.Operator.GT, "2019-01-11"), Pageable.unpaged());
        Assertions.assertEquals(Set.of(4L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateLessThanOrEqualTo() {
        final Page<Root> page = repo.findAll(null, filter(LocalDateCompare.Operator.LTE, "2019-01-11"), Pageable.unpaged());
        Assertions.assertEquals(Set.of(1L, 2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateGreaterThanOrEqualTo() {
        final Page<Root> page = repo.findAll(null, filter(LocalDateCompare.Operator.GTE, "2019-01-11"), Pageable.unpaged());
        Assertions.assertEquals(Set.of(2L, 3L, 4L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateBetween() {
        final Page<Root> page = repo.findAll(null, filter(LocalDateCompare.Operator.BETWEEN, "2019-01-11", "2019-09-30"), Pageable.unpaged());
        Assertions.assertEquals(Set.of(2L, 3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void filteringWithNeqIncludesNullValues() {
        final Page<Root> all = repo.findAll(Pageable.unpaged());
        final Page<Root> page = repo.findAll(null, filter(LocalDateCompare.Operator.NEQ, "2222-02-02"), Pageable.unpaged());
        Assertions.assertEquals(all.getTotalElements(), page.getTotalElements());
    }

    private static FilterRequest filter(LocalDateCompare.Operator operator, String... values) {
        return FilterRequest.builder()
                .localDate("date", f -> f.of(operator, values))
                .build();
    }

    private static Root entity(long id, LocalDate date) {
        final Root e = new Root();
        e.id = id;
        e.date = date;
        return e;
    }
}
