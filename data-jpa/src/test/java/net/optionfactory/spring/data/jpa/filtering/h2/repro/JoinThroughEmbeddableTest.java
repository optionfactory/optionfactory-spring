package net.optionfactory.spring.data.jpa.filtering.h2.repro;

import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
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
        //repo.deleteAll();
    }

    @Test
    public void canFilterThrougAnEmbeddableJoin() {

        final var fr = FilterRequest.builder()
                .number("byLeafId", f -> f.of(NumberCompare.Operator.EQ, "1"))
                .build();

        final var page = repo.findAll(fr, Pageable.unpaged());
    }

}
