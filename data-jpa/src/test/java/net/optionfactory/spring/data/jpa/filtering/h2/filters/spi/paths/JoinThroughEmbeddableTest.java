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
public class JoinThroughEmbeddableTest {

    public interface JoinThroughEmbeddableRepository extends JpaRepository<JoinThroughEmbeddableEntity, Long>, WhitelistFilteringRepository<JoinThroughEmbeddableEntity> {

    }

    @Entity
    @NumberCompare(name = "byLeafVal", path = "a.b.val")
    public static class JoinThroughEmbeddableEntity {

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
        public Integer val;
    }

    @Inject
    private JoinThroughEmbeddableRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAllInBatch();
        final var e1 = new JoinThroughEmbeddableEntity();
        e1.id = 1;
        e1.a = new Embed();
        e1.a.b = new Leaf();
        e1.a.b.id = 1;
        e1.a.b.val = 1;
        final var e2 = new JoinThroughEmbeddableEntity();
        e2.id = 2;
        e2.a = new Embed();
        e2.a.b = new Leaf();
        e2.a.b.id = 2;
        e2.a.b.val = null;
        final var e3 = new JoinThroughEmbeddableEntity();
        e3.id = 3;
        e3.a = new Embed();
        e3.a.b = null;

        repo.save(e1);
        repo.save(e2);
        repo.save(e3);
    }

    @Test
    public void canFilterThroughAnEmbeddableJoin() {
        final var fr = FilterRequest.builder()
                .number("byLeafVal", f -> f.of(NumberCompare.Operator.EQ, "1"))
                .build();

        final var page = repo.findAll(fr, Pageable.unpaged());
        Assert.assertEquals(1, page.getTotalElements());
    }

    @Test
    public void canFilterInequalityThroughAnEmbeddableJoinWithNullAssociation() {
        final var fr = FilterRequest.builder()
                .number("byLeafVal", f -> f.of(NumberCompare.Operator.NEQ, "999"))
                .build();

        final var page = repo.findAll(fr, Pageable.unpaged());

        Assert.assertEquals(3, page.getTotalElements());
    }

}
