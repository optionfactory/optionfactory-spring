package net.optionfactory.data.jpa.filtering.filters;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.CustomEntity;
import net.optionfactory.data.jpa.filtering.CustomFilter;
import net.optionfactory.data.jpa.filtering.CustomsRepository;
import net.optionfactory.data.jpa.filtering.FilterRequest;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;
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

    @Test(expected = Filters.InvalidFilterRequest.class)
    public void throwsWhenCustomFilterDoesNotMeetParametersPreconditions() {
        final FilterRequest request = new FilterRequest();
        request.put("custom", new String[0]);
        customs.findAll(request, Pageable.unpaged());
    }

    @Test
    public void canApplyCustomFilterWithParameter() {
        final FilterRequest request = new FilterRequest();
        request.put("custom", new String[]{CustomFilter.Check.LESS.name()});
        final Page<CustomEntity> page = customs.findAll(request, Pageable.unpaged());
        Assert.assertEquals(new HashSet<>(Arrays.asList(3L, 5L)), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }
}
