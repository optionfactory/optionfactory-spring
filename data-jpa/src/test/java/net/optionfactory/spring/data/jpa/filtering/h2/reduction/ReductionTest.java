package net.optionfactory.spring.data.jpa.filtering.h2.reduction;

import java.util.Arrays;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@Transactional
public class ReductionTest {

    @Autowired
    public NumberEntityRepository repo;

    @BeforeEach
    public void setup() {
        repo.saveAll(Arrays.asList(
                entity(1, 3),
                entity(2, 15),
                entity(3, 10),
                entity(4, 5)
        ));
    }

    @Test
    public void canPerformReductionWithoutFiltering() {
        final ReductionNumberEntityRepository.Reduction reduced = repo.reduce(FilterRequest.builder().build());
        Assertions.assertEquals(4, reduced.count());
        Assertions.assertEquals(3, reduced.min());
        Assertions.assertEquals(15, reduced.max());
        Assertions.assertEquals(8.25, reduced.average(), 0.0);
    }

    @Test
    public void canPerformReductionWithFiltering() {
        final ReductionNumberEntityRepository.Reduction reduced = repo.reduce(FilterRequest.builder()
                .number("number", filter -> filter.gt(8))
                .build());
        Assertions.assertEquals(2, reduced.count());
        Assertions.assertEquals(10, reduced.min());
        Assertions.assertEquals(15, reduced.max());
        Assertions.assertEquals(12.5, reduced.average(), 0.0);
    }

    private NumberEntity entity(int id, int number) {
        final var e = new NumberEntity();
        e.id = id;
        e.number = number;
        return e;
    }

}
