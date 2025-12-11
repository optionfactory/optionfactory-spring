package net.optionfactory.spring.data.jpa.filtering.h2.filters.inlist;

import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@Transactional
public class InListTest {

    @Autowired
    private EntityForInListRepository repo;

    @BeforeEach
    public void setup() {
        repo.save(entity(1, "swimming", 1, 10));
        repo.save(entity(2, "skiing", 1, 10));
        repo.save(entity(3, "walking", Math.PI, null));
        repo.save(entity(4, "cooking", 2.3e5, 5));
    }

    @Test
    public void canFilterByInListOnStringField() {
        final var fr = FilterRequest.builder()
                .inList("nameIn", "walking", "skiing", "sleeping")
                .build();
        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInEmptyListYieldingAnEmptyResult() {
        final var fr = FilterRequest.builder()
                .inList("nameIn")
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(0L, page.getTotalElements());
    }

    @Test
    public void canFilterByInListOnNonNullBoxedNumber() {
        final var fr = FilterRequest.builder()
                .inList("maxPersonsIn", "10", "11", "12")
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(1L, 2L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnNullBoxedNumber() {
        final var fr = FilterRequest.builder()
                .inList("maxPersonsIn", null, "5")
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnPrimitiveNumber() {
        final var fr = FilterRequest.builder()
                .inList("ratingIn", Double.toString(2.3e5d), Double.toString(Math.PI))
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<EntityForInList> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
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
