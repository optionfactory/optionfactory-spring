package net.optionfactory.spring.data.jpa.filtering.filters.inlist;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
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
    private EntityForInListRepository repo;

    @Before
    public void setup() {
        repo.save(entity(1, "swimming", 1, 10));
        repo.save(entity(2, "skiing", 1, 10));
        repo.save(entity(3, "walking", Math.PI, null));
        repo.save(entity(4, "cooking", 2.3e5, 5));
    }

    @Test
    public void canFilterByInListOnStringField() {
        final FilterRequest fr = FilterRequest.of(Map.of("nameIn", new String[]{"walking", "skiing", "sleeping"}));
        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assert.assertEquals(Set.of(2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInEmptyListYieldingAnEmptyResult() {
        final FilterRequest fr = FilterRequest.of(Map.of("nameIn", new String[0]));
        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assert.assertEquals(0L, page.getTotalElements());
    }

    @Test
    public void canFilterByInListOnNonNullBoxedNumber() {
        final FilterRequest fr = FilterRequest.of(Map.of("maxPersonsIn", new String[]{"10", "11", "12"}));
        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assert.assertEquals(Set.of(1L, 2L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnNullBoxedNumber() {
        final FilterRequest fr = FilterRequest.of(Map.of("maxPersonsIn", new String[]{null, "5"}));
        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assert.assertEquals(Set.of(3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnPrimitiveNumber() {
        final FilterRequest fr = FilterRequest.of(Map.of("ratingIn", new String[]{Double.toString(2.3e5d), Double.toString(Math.PI)}));
        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assert.assertEquals(Set.of(3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    private static EntityForInList entity(long id, String name, double rating, Integer maxPersons) {
        final EntityForInList activity = new EntityForInList();
        activity.id = id;
        activity.name = name;
        activity.maxPersons = maxPersons;
        activity.rating = rating;
        return activity;
    }
}
