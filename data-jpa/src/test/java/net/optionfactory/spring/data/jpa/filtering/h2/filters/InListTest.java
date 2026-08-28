package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.InList;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class InListTest {

    @Entity
    @InList(name = "nameIn", path = "name")
    @InList(name = "maxPersonsIn", path = "maxPersons")
    @InList(name = "ratingIn", path = "rating")
    public static class Root {

        @Id
        public long id;
        public String name;
        public Integer maxPersons;
        public double rating;

    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    private RootsRepository repo;

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
        final Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(2L, 3L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInEmptyListYieldingAnEmptyResult() {
        final var fr = FilterRequest.builder()
                .inList("nameIn")
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(0L, page.getTotalElements());
    }

    @Test
    public void canFilterByInListOnNonNullBoxedNumber() {
        final var fr = FilterRequest.builder()
                .inList("maxPersonsIn", "10", "11", "12")
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(1L, 2L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnNullBoxedNumber() {
        final var fr = FilterRequest.builder()
                .inList("maxPersonsIn", null, "5")
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    @Test
    public void canFilterByInListOnPrimitiveNumber() {
        final var fr = FilterRequest.builder()
                .inList("ratingIn", Double.toString(2.3e5d), Double.toString(Math.PI))
                .build();

        final Pageable pr = Pageable.unpaged();
        final Page<Root> page = repo.findAll(null, fr, pr);
        Assertions.assertEquals(Set.of(3L, 4L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }

    private static Root entity(long id, String name, double rating, Integer maxPersons) {
        final Root activity = new Root();
        activity.id = id;
        activity.name = name;
        activity.maxPersons = maxPersons;
        activity.rating = rating;
        return activity;
    }
}
