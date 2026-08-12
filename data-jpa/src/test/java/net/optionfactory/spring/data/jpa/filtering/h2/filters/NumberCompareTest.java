package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class NumberCompareTest {

    @Entity
    @NumberCompare(name = "maxPersons", path = "maxPersons")
    @NumberCompare(name = "rating", path = "rating")
    @NumberCompare(name = "container.value", path = "container.value")
    public static class Root {

        @Id
        public long id;
        public Integer maxPersons;
        public double rating;

        @Embedded
        public Embed container;

    }

    @Embeddable
    public static class Embed {

        public Integer value;
    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    public RootsRepository repo;

    @BeforeEach
    public void setup() {
        repo.saveAll(Arrays.asList(
                entity(1, null, Math.E, 42),
                entity(2, 15, Math.E, 43),
                entity(3, 10, Math.PI, 44),
                entity(4, 5, 2.3e5, 45)
        ));
    }

    @Test
    public void canFilterEqualityByNullValue() {
        final Page<Root> page = repo.findAll(null, filter("maxPersons", NumberCompare.Operator.EQ, null), Pageable.unpaged());
        Assertions.assertEquals(Set.of(1L), idsIn(page));
    }

    @Test
    public void cannotFilterInequalityByNullValue() {
        Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> {
            repo.findAll(null, filter("maxPersons", NumberCompare.Operator.LTE, null), Pageable.unpaged());
        });
    }

    @Test
    public void canFilterByBoxedValue() {
        Assertions.assertEquals(Set.of(3L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.EQ, "10"), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(4L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.LT, "10"), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(2L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.GT, "10"), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(3L, 4L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.LTE, "10"), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(2L, 3L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.GTE, "10"), Pageable.unpaged())));
    }

    @Test
    public void canFilterByPrimitiveValue() {
        Assertions.assertEquals(Set.of(3L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.EQ, Double.toString(Math.PI)), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(1L, 2L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.LT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(4L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.GT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(1L, 2L, 3L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.LTE, Double.toString(Math.PI)), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(3L, 4L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.GTE, Double.toString(Math.PI)), Pageable.unpaged())));
    }

    @Test
    public void canFilterOnEmbeddedValues() {
        Assertions.assertEquals(Set.of(1L), idsIn(repo.findAll(null, filter("container.value", NumberCompare.Operator.EQ, "42"), Pageable.unpaged())));
    }

    @Test
    public void filteringWithNeqIncludesNullValues() {
        final Page<Root> all = repo.findAll(Pageable.unpaged());
        final Page<Root> page = repo.findAll(null, filter("maxPersons", NumberCompare.Operator.NEQ, "9999"), Pageable.unpaged());
        Assertions.assertEquals(all.getTotalElements(), page.getTotalElements());
    }

    @Test
    public void canFilterByBetweenRange() {
        final Set<Long> expected = Set.of(2L, 3L);
        Assertions.assertEquals(expected, idsIn(repo.findAll(null, between("maxPersons", "10", "15"), Pageable.unpaged())));
        Assertions.assertEquals(expected, idsIn(repo.findAll(null, between("maxPersons", "15", "10"), Pageable.unpaged())));
    }

    private static FilterRequest between(String filterName, String lo, String hi) {
        return FilterRequest.builder()
                .number(filterName, f -> f.of(NumberCompare.Operator.BETWEEN, lo, hi))
                .build();
    }

    private static FilterRequest filter(String filterName, NumberCompare.Operator operator, String value) {
        return FilterRequest.builder()
                .number(filterName, f -> f.of(operator, value))
                .build();
    }

    private static Set<Long> idsIn(Page<Root> page) {
        return page.getContent().stream().map(flag -> flag.id).collect(Collectors.toSet());
    }

    private static Root entity(long id, Integer maxPersons, double rating, Integer containerValue) {
        final Root e = new Root();
        e.id = id;
        e.maxPersons = maxPersons;
        e.rating = rating;
        e.container = new Embed();
        e.container.value = containerValue;
        return e;
    }
}
