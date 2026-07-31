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
public class EmbeddableTraversalOverrideTest {

    public interface EmbeddableTraversalOverrideRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {
    }

    @Inject
    private EmbeddableTraversalOverrideRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        final var entity = new Root();
        entity.id = 1;
        entity.a = new Embed();
        entity.a.b = new Leaf();
        entity.a.b.id = 42;
        repo.save(entity);
    }

    @Test
    public void respectsFilterTraversalOverrideOnEmbeddablePrefix() {
        final var fr = FilterRequest.builder()
                .number("byLeafId", f -> f.of(NumberCompare.Operator.EQ, "42"))
                .build();
        final var page = repo.findAll(fr, Pageable.unpaged());
        Assert.assertEquals(1, page.getTotalElements());
    }

    @Entity
    @FilterTraversal(path = "a", joinType = JoinType.INNER) // Explicit user override on @Embedded hop
    @NumberCompare(name = "byLeafId", path = "a.b.id")
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
    }

    @Entity
    public static class Leaf {

        @Id
        public long id;
    }

}
