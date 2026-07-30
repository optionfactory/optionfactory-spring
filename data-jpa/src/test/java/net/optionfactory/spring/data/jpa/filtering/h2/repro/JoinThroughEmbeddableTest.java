package net.optionfactory.spring.data.jpa.filtering.h2.repro;

import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@Transactional
public class JoinThroughEmbeddableTest {

    @Autowired
    private JoinThroughEmbeddableRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        final var entity = new JoinThroughEmbeddableEntity();
        entity.id = 1;
        entity.a = new JoinThroughEmbeddableEntity.MyEmbeddable();
        entity.a.b = new JoinThroughEmbeddableEntity.MyLeaf();
        entity.a.b.id = 1;
        repo.save(entity);
    }

    @Test
    public void canFilterThroughAnEmbeddableJoin() {

        final var fr = FilterRequest.builder()
                .number("byLeafId", f -> f.of(NumberCompare.Operator.EQ, "1"))
                .build();

        final var page = repo.findAll(fr, Pageable.unpaged());
        Assert.assertEquals(1, page.getTotalElements());
    }

    @Test
    public void canFilterThroughAnEmbeddableJoinWithZeroResults() {

        final var fr = FilterRequest.builder()
                .number("byLeafId", f -> f.of(NumberCompare.Operator.EQ, "0"))
                .build();

        final var page = repo.findAll(fr, Pageable.unpaged());
        Assert.assertEquals(0, page.getTotalElements());
    }


}
