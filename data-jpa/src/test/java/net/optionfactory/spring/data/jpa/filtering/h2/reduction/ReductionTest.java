package net.optionfactory.spring.data.jpa.filtering.h2.reduction;

import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnH2TestConfig.class)
@Transactional
public class ReductionTest {

    @Autowired
    public NumberEntityRepository repo;

    @Before
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
        Assert.assertEquals(4, reduced.count());
        Assert.assertEquals(3, reduced.min());
        Assert.assertEquals(15, reduced.max());
        Assert.assertEquals(8.25, reduced.average(), 0.0);
    }

    @Test
    public void canPerformReductionWithFiltering() {
        final ReductionNumberEntityRepository.Reduction reduced = repo.reduce(FilterRequest.builder()
                .number("number", filter -> filter.gt(8))
                .build());
        Assert.assertEquals(2, reduced.count());
        Assert.assertEquals(10, reduced.min());
        Assert.assertEquals(15, reduced.max());
        Assert.assertEquals(12.5, reduced.average(), 0.0);
    }

    private NumberEntity entity(int id, int number) {
        final var e = new NumberEntity();
        e.id = id;
        e.number = number;
        return e;
    }

}
