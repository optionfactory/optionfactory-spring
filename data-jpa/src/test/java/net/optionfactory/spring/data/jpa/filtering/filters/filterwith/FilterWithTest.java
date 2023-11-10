package net.optionfactory.spring.data.jpa.filtering.filters.filterwith;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateTestConfig.class)
@Transactional
public class FilterWithTest {

    @Autowired
    private CustomsRepository customs;

    @Before
    public void setup() {
        customs.saveAll(Arrays.asList(
                custom(1, 1),
                custom(2, 1),
                custom(3, 12),
                custom(4, -1),
                custom(5, 6),
                custom(6, 5)
        ));
    }

    private static CustomEntity custom(long id, long x) {
        final CustomEntity custom = new CustomEntity();
        custom.id = id;
        custom.x = x;
        return custom;
    }

    @Test(expected = InvalidFilterRequest.class)
    public void throwsWhenCustomFilterDoesNotMeetParametersPreconditions() {
        final FilterRequest request = FilterRequest.of(Map.of("custom", new String[0]));
        customs.findAll(null, request, Pageable.unpaged());
    }

    @Test
    public void canApplyCustomFilterWithParameter() {
        final FilterRequest request = FilterRequest.of(Map.of("custom", new String[]{CustomFilter.Check.LESS.name()}));
        final Page<CustomEntity> page = customs.findAll(null, request, Pageable.unpaged());
        Assert.assertEquals(Set.of(3L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }
}
