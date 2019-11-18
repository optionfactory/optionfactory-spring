package net.optionfactory.data.jpa.filtering.filters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.ActivitiesRepository;
import net.optionfactory.data.jpa.filtering.Activity;
import net.optionfactory.data.jpa.filtering.FilterRequest;
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
    public ActivitiesRepository activities;

    @Before
    public void setup() {
        activities.saveAll(Arrays.asList(
                activity(1, "walking", null, Math.E),
                activity(2, "swimming", 15, Math.E),
                activity(3, "skiing", 10, Math.PI),
                activity(4, "cooking", 5, 2.3e5)
        ));
    }

    @Test
    public void canFilterEqualityByNullValue() {
        final Page<Activity> page = activities.findAll(filter("maxPersons", NumberCompare.Operator.EQ, null), Pageable.unpaged());
        Assert.assertEquals(ids(1L), idsIn(page));
    }

    @Test(expected = Filters.InvalidFilterRequest.class)
    public void cannotFilterInequalityByNullValue() {
        activities.findAll(filter("maxPersons", NumberCompare.Operator.LTE, null), Pageable.unpaged());
    }

    @Test
    public void canFilterByBoxedValue() {
        Assert.assertEquals(ids(3L), idsIn(activities.findAll(filter("maxPersons", NumberCompare.Operator.EQ, "10"), Pageable.unpaged())));
        Assert.assertEquals(ids(4L), idsIn(activities.findAll(filter("maxPersons", NumberCompare.Operator.LT, "10"), Pageable.unpaged())));
        Assert.assertEquals(ids(2L), idsIn(activities.findAll(filter("maxPersons", NumberCompare.Operator.GT, "10"), Pageable.unpaged())));
        Assert.assertEquals(ids(3L, 4L), idsIn(activities.findAll(filter("maxPersons", NumberCompare.Operator.LTE, "10"), Pageable.unpaged())));
        Assert.assertEquals(ids(2L, 3L), idsIn(activities.findAll(filter("maxPersons", NumberCompare.Operator.GTE, "10"), Pageable.unpaged())));
    }

    @Test
    public void canFilterByPrimitiveValue() {
        Assert.assertEquals(ids(3L), idsIn(activities.findAll(filter("rating", NumberCompare.Operator.EQ, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(ids(1L, 2L), idsIn(activities.findAll(filter("rating", NumberCompare.Operator.LT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(ids(4L), idsIn(activities.findAll(filter("rating", NumberCompare.Operator.GT, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(ids(1L, 2L, 3L), idsIn(activities.findAll(filter("rating", NumberCompare.Operator.LTE, Double.toString(Math.PI)), Pageable.unpaged())));
        Assert.assertEquals(ids(3L, 4L), idsIn(activities.findAll(filter("rating", NumberCompare.Operator.GTE, Double.toString(Math.PI)), Pageable.unpaged())));
    }

    private static FilterRequest filter(String filterName, NumberCompare.Operator operator, String value) {
        final FilterRequest fr = new FilterRequest();
        fr.put(filterName, new String[]{operator.name(), value});
        return fr;
    }

    private static Set<Long> ids(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static Set<Long> idsIn(Page<Activity> page) {
        return page.getContent().stream().map(flag -> flag.id).collect(Collectors.toSet());
    }

    private static Activity activity(long id, String name, Integer maxPersons, double rating) {
        final Activity activity = new Activity();
        activity.id = id;
        activity.name = name;
        activity.description = name;
        activity.season = Activity.Season.WINTER;
        activity.maxPersons = maxPersons;
        activity.rating = rating;
        return activity;
    }
}
