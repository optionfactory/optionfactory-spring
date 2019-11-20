package net.optionfactory.data.jpa.filtering.filters.numbers;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.FilterRequest;
import net.optionfactory.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;
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
@ContextConfiguration(classes = HibernateTestConfig.class)
@Transactional
public class NumberCompareTest {

    @Autowired
    public EntityForNumberCompareRepository repo;

    @Before
    public void setup() {
        repo.saveAll(Arrays.asList(
                entity(1, null, Math.E),
                entity(2, 15, Math.E),
                entity(3, 10, Math.PI),
                entity(4, 5, 2.3e5)
        ));
    }

    @Test
    public void canFilterEqualityByNullValue() {
        final Page<EntityForNumberCompare> page = repo.findAll(filter("maxPersons", NumberCompare.Operator.EQ, null), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L), idsIn(page));
    }

    @Test(expected = Filters.InvalidFilterRequest.class)
    public void cannotFilterInequalityByNullValue() {
        repo.findAll(filter("maxPersons", NumberCompare.Operator.LTE, null), Pageable.unpaged());
    }

    @Test
    public void canFilterByBoxedValue() {
        Assert.assertEquals(Set.of(3L), idsIn(repo.findAll(filter("maxPersons", NumberCompare.Operator.EQ, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(4L), idsIn(repo.findAll(filter("maxPersons", NumberCompare.Operator.LT, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(2L), idsIn(repo.findAll(filter("maxPersons", NumberCompare.Operator.GT, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(3L, 4L), idsIn(repo.findAll(filter("maxPersons", NumberCompare.Operator.LTE, "10"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(2L, 3L), idsIn(repo.findAll(filter("maxPersons", NumberCompare.Operator.GTE, "10"), Pageable.unpaged())));
    }

    @Test
    public void canFilterByPrimitiveValue() {
        Assert.assertEquals(Set.of(3L), idsIn(repo.findAll(filter("rating", NumberCompare.Operator.EQ, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(1L, 2L), idsIn(repo.findAll(filter("rating", NumberCompare.Operator.LT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(4L), idsIn(repo.findAll(filter("rating", NumberCompare.Operator.GT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(1L, 2L, 3L), idsIn(repo.findAll(filter("rating", NumberCompare.Operator.LTE, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(Set.of(3L, 4L), idsIn(repo.findAll(filter("rating", NumberCompare.Operator.GTE, Double.toString(Math.PI)), Pageable.unpaged())));
    }

    private static FilterRequest filter(String filterName, NumberCompare.Operator operator, String value) {
        final FilterRequest fr = new FilterRequest();
        fr.put(filterName, new String[]{operator.name(), value});
        return fr;
    }


    private static Set<Long> idsIn(Page<EntityForNumberCompare> page) {
        return page.getContent().stream().map(flag -> flag.id).collect(Collectors.toSet());
    }

    private static EntityForNumberCompare entity(long id, Integer maxPersons, double rating) {
        final EntityForNumberCompare e = new EntityForNumberCompare();
        e.id = id;
        e.maxPersons = maxPersons;
        e.rating = rating;
        return e;
    }
}
