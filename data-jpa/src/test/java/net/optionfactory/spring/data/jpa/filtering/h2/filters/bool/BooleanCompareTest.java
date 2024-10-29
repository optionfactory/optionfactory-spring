package net.optionfactory.spring.data.jpa.filtering.h2.filters.bool;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
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
@ContextConfiguration(classes = HibernateOnH2TestConfig.class)
@Transactional
public class BooleanCompareTest {

    @Autowired
    private FlagsRepository flags;

    @Before
    public void setup() {
        final Flag trueFlag = new Flag();
        trueFlag.id = 1;
        trueFlag.data = true;
        final Flag falseFlag = new Flag();
        falseFlag.id = 2;
        falseFlag.data = false;
        flags.saveAll(Arrays.asList(trueFlag, falseFlag));
    }

    @Test
    public void canFilterBooleanValueWithDefaultOptions() {
        Assert.assertEquals(Set.of(1L), idsIn(flags.findAll(null, filter("javaBoolean", "true"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(2L), idsIn(flags.findAll(null, filter("javaBoolean", "false"), Pageable.unpaged())));
    }

    @Test
    public void canFilterBooleanValueWithCustomValues() {
        Assert.assertEquals(Set.of(1L), idsIn(flags.findAll(null, filter("YNMatchCaseBoolean", "Y"), Pageable.unpaged())));
        Assert.assertEquals(Set.of(2L), idsIn(flags.findAll(null, filter("YNMatchCaseBoolean", "N"), Pageable.unpaged())));
    }

    @Test(expected = InvalidFilterRequest.class)
    public void throwsWhenValueDoesNotMatch() {
        flags.findAll(null, filter("yesNoBoolean", "maybe"), Pageable.unpaged());
    }

    private static FilterRequest filter(String filterName, String value) {
        return FilterRequest.builder()
                .bool(filterName, f -> f.eq(value))
                .build();
    }

    private static Set<Long> idsIn(Page<Flag> page) {
        return page.getContent().stream().map(flag -> flag.id).collect(Collectors.toSet());
    }
}
