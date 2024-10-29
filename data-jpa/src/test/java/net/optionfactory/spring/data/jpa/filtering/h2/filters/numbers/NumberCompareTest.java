package net.optionfactory.spring.data.jpa.filtering.h2.filters.numbers;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnH2TestConfig.class)
@Transactional
public class NumberCompareTest {

    @Autowired
    public EntityForNumberCompareRepository repo;

    @Before
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
        final Page<EntityForNumberCompare> page = repo.findAll(null, filter("maxPersons", NumberCompare.Operator.EQ, null), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L), idsIn(page));
    }

    @Test(expected = InvalidFilterRequest.class)
    public void cannotFilterInequalityByNullValue() {
        repo.findAll(null, filter("maxPersons", NumberCompare.Operator.LTE, null), Pageable.unpaged());
    }

    @Test
    public void canFilterByBoxedValue() {
        Assert.assertEquals(Set.of(3L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.EQ, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(4L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.LT, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(2L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.GT, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(3L, 4L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.LTE, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(2L, 3L), idsIn(repo.findAll(null, filter("maxPersons", NumberCompare.Operator.GTE, "10"), Pageable.unpaged())));
    }

    @Test
    public void canFilterByPrimitiveValue() {
        Assert.assertEquals(Set.of(3L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.EQ, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(1L, 2L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.LT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(4L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.GT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(1L, 2L, 3L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.LTE, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(3L, 4L), idsIn(repo.findAll(null, filter("rating", NumberCompare.Operator.GTE, Double.toString(Math.PI)), Pageable.unpaged())));
    }

    @Test
    public void canFilterOnEmbeddedValues() {
        Assert.assertEquals(Set.of(1L), idsIn(repo.findAll(null, filter("container.value", NumberCompare.Operator.EQ, "42"), Pageable.unpaged())));
    }

    @Test
    public void filteringWithNeqIncludesNullValues() {
        final Page<EntityForNumberCompare> all = repo.findAll(Pageable.unpaged());
        final Page<EntityForNumberCompare> page = repo.findAll(null, filter("maxPersons", NumberCompare.Operator.NEQ, "9999"), Pageable.unpaged());
        Assert.assertEquals(all.getTotalElements(), page.getTotalElements());
    }

    private static FilterRequest filter(String filterName, NumberCompare.Operator operator, String value) {
        return FilterRequest.builder()
                .number(filterName, f -> f.of(operator, value))
                .build();
    }

    private static Set<Long> idsIn(Page<EntityForNumberCompare> page) {
        return page.getContent().stream().map(flag -> flag.id).collect(Collectors.toSet());
    }

    private static EntityForNumberCompare entity(long id, Integer maxPersons, double rating, Integer containerValue) {
        final EntityForNumberCompare e = new EntityForNumberCompare();
        e.id = id;
        e.maxPersons = maxPersons;
        e.rating = rating;
        e.container = new EntityForNumberCompare.NumericEmbeddedContainer();
        e.container.value = containerValue;
        return e;
    }
}
