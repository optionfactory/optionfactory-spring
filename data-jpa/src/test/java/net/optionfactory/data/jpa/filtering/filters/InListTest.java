package net.optionfactory.data.jpa.filtering.filters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.ActivitiesRepository;
import net.optionfactory.data.jpa.filtering.Activity;
import net.optionfactory.data.jpa.filtering.FilterRequest;
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
public class InListTest {

    @Autowired
    private ActivitiesRepository activities;

    @Before
    public void setup() {
        activities.save(activity(1, "swimming", 1, 10));
        activities.save(activity(2, "skiing", 1, 10));
        activities.save(activity(3, "walking", Math.PI, null));
        activities.save(activity(4, "cooking", 2.3e5, 5));
    }

    @Test
    public void canFilterByInListOnStringField() {
        final FilterRequest fr = new FilterRequest();
        fr.put("nameIn", new String[]{"walking", "skiing", "sleeping"});
        final Pageable pr = Pageable.unpaged();
        final Page<Activity> page = activities.findAll(fr, pr);
        Assert.assertEquals(new HashSet<>(Arrays.asList(2L, 3L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInEmptyListYieldingAnEmptyResult() {
        final FilterRequest fr = new FilterRequest();
        fr.put("nameIn", new String[0]);
        final Pageable pr = Pageable.unpaged();
        final Page<Activity> page = activities.findAll(fr, pr);
        Assert.assertEquals(0L, page.getTotalElements());
    }

    @Test
    public void canFilterByInListOnNonNullBoxedNumber() {
        final FilterRequest fr = new FilterRequest();
        fr.put("maxPersonsIn", new String[]{"10", "11", "12"});
        final Pageable pr = Pageable.unpaged();
        final Page<Activity> page = activities.findAll(fr, pr);
        Assert.assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnNullBoxedNumber() {
        final FilterRequest fr = new FilterRequest();
        fr.put("maxPersonsIn", new String[]{null, "5"});
        final Pageable pr = Pageable.unpaged();
        final Page<Activity> page = activities.findAll(fr, pr);
        Assert.assertEquals(new HashSet<>(Arrays.asList(3L, 4L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnPrimitiveNumber() {
        final FilterRequest fr = new FilterRequest();
        fr.put("ratingIn", new String[]{Double.toString(2.3e5d), Double.toString(Math.PI)});
        final Pageable pr = Pageable.unpaged();
        final Page<Activity> page = activities.findAll(fr, pr);
        Assert.assertEquals(new HashSet<>(Arrays.asList(3L, 4L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    private static Activity activity(long id, String name, double rating, Integer maxPersons) {
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
