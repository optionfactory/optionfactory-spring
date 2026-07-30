package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi.paths;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
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
public class DeepEmbeddableTest {

    public interface DeepEmbeddableRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {
    }

    @Inject
    private DeepEmbeddableRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        final var entity = new Root();
        entity.id = 1;
        entity.emb1 = new Emb1();
        entity.emb1.emb2 = new Emb2();
        entity.emb1.emb2.leaf = new Leaf();
        entity.emb1.emb2.leaf.id = 100;
        repo.save(entity);
    }

    @Test
    public void canFilterThroughDeeplyNestedEmbeddableAssociation() {
        final var fr = FilterRequest.builder()
                .number("byLeafId", f -> f.of(NumberCompare.Operator.EQ, "100"))
                .build();
        final var page = repo.findAll(fr, Pageable.unpaged());
        Assert.assertEquals(1, page.getTotalElements());
    }

    @Entity
    @NumberCompare(name = "byLeafId", path = "emb1.emb2.leaf.id")
    public static class Root {

        @Id
        public long id;

        @Embedded
        public Emb1 emb1;

    }

    @Embeddable
    public static class Emb1 {

        @Embedded
        public Emb2 emb2;
    }

    @Embeddable
    public static class Emb2 {

        @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
        public Leaf leaf;
    }

    @Entity
    public static class Leaf {

        @Id
        public long id;
    }

}
