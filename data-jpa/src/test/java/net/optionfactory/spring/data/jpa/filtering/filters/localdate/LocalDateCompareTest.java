package net.optionfactory.spring.data.jpa.filtering.filters.localdate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;
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
public class LocalDateCompareTest {

    @Autowired
    private EntityForLocalDateRepository repo;

    @Before
    public void setup() {
        repo.saveAll(Arrays.asList(
                entity(1, LocalDate.parse("2019-01-10")),
                entity(2, LocalDate.parse("2019-01-11")),
                entity(3, LocalDate.parse("2019-01-11")),
                entity(4, LocalDate.parse("2019-02-25")),
                entity(5, LocalDate.parse("2019-10-01"))
        ));
    }

    @Test
    public void canFilterByLocalDateEquality() {
        final Page<EntityForLocalDate> page = repo.findAll(null, filter(LocalDateCompare.Operator.EQ, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(Set.of(2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateLessThan() {
        final Page<EntityForLocalDate> page = repo.findAll(null, filter(LocalDateCompare.Operator.LT, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateGreaterThan() {
        final Page<EntityForLocalDate> page = repo.findAll(null, filter(LocalDateCompare.Operator.GT, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(Set.of(4L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateLessThanOrEqualTo() {
        final Page<EntityForLocalDate> page = repo.findAll(null, filter(LocalDateCompare.Operator.LTE, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(Set.of(1L, 2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateGreaterThanOrEqualTo() {
        final Page<EntityForLocalDate> page = repo.findAll(null, filter(LocalDateCompare.Operator.GTE, "2019-01-11"), Pageable.unpaged());
        Assert.assertEquals(Set.of(2L, 3L, 4L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByLocalDateBetween() {
        final Page<EntityForLocalDate> page = repo.findAll(null, filter(LocalDateCompare.Operator.BETWEEN, "2019-01-11", "2019-09-30"), Pageable.unpaged());
        Assert.assertEquals(Set.of(2L, 3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    private static FilterRequest filter(LocalDateCompare.Operator operator, String... values) {
        return FilterRequest.of(Map.of("date", Stream.concat(Stream.of(operator.name()), Stream.of(values)).toArray(i -> new String[i])));
    }

    private static EntityForLocalDate entity(long id, LocalDate date) {
        final EntityForLocalDate e = new EntityForLocalDate();
        e.id = id;
        e.date = date;
        return e;
    }
}
