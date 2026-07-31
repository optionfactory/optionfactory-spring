package net.optionfactory.spring.data.jpa.filtering.h2.paths;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.criteria.JoinType;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class JoinThroughEmbeddableWithJoinTypesTest {

    @Entity
    @FilterTraversal(path = "a.b", joinType = JoinType.INNER)
    @FilterTraversal(path = "a.c", joinType = JoinType.LEFT)
    @NumberCompare(name = "byLeafB", path = "a.b.val")
    @NumberCompare(name = "byLeafC", path = "a.c.val")
    public static class Root {

        @Id
        public long id;
        @Embedded
        public Embed a;
    }

    @Embeddable
    public static class Embed {

        @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
        public Leaf b;
        @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
        public Leaf c;
    }

    @Entity
    public static class Leaf {

        @Id
        public long id;
        public Integer val;
    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    private RootsRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        final var e1 = new Root();
        e1.id = 1;
        e1.a = new Embed();
        e1.a.b = new Leaf();
        e1.a.b.id = 1;
        e1.a.b.val = 10;
        e1.a.c = new Leaf();
        e1.a.c.id = 2;
        e1.a.c.val = 20;
        repo.save(e1);
    }

    @Test
    public void canFilterOnSiblingAssociationsInsideEmbeddableWithDifferentJoinTypes() {
        final var fr = FilterRequest.builder()
                .number("byLeafB", f -> f.eq(10))
                .number("byLeafC", f -> f.eq(20))
                .build();

        final var page = repo.findAll(fr, Pageable.unpaged());
        Assert.assertEquals(1, page.getTotalElements());
    }

}
