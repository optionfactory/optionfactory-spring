package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi.paths;

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

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnH2TestConfig.class)
@Transactional
public class ModeSubselectTest {

    @Autowired
    private RootEntityWithSubselectFiltersRepository roots;

    @Before
    public void setup() {
        roots.save(RootEntityWithSubselectFilters.of(
                LeafEntityWithSubselectFilters.of(true, true),
                LeafEntityWithSubselectFilters.of(true, false),
                LeafEntityWithSubselectFilters.of(false, true),
                LeafEntityWithSubselectFilters.of(false, false)
        ));
        roots.save(RootEntityWithSubselectFilters.of(
                LeafEntityWithSubselectFilters.of(true, false),
                LeafEntityWithSubselectFilters.of(false, true)
        ));
        roots.save(RootEntityWithSubselectFilters.of(
                LeafEntityWithSubselectFilters.of(true, false),
                LeafEntityWithSubselectFilters.of(false, false)
        ));
    }

    @Test
    public void filtersOnJoinedEntity() {
        //usually when using multiple filters on a PluralAttribute what you
        //want is any root where the filters apply at least once for any
        
        final var fr = FilterRequest.builder()
                .bool("flag1", f -> f.eq(Boolean.TRUE))
                .bool("flag2", f -> f.eq(Boolean.TRUE))
                .build();
        
        Assert.assertEquals(2, roots.findAll(null, fr).size());
    }
}
