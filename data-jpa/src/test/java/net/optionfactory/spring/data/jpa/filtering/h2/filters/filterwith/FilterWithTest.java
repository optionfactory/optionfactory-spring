package net.optionfactory.spring.data.jpa.filtering.h2.filters.filterwith;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@Transactional
public class FilterWithTest {

    @Autowired
    private CustomsRepository customs;

    @BeforeEach
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

    @Test
    public void throwsWhenCustomFilterDoesNotMeetParametersPreconditions() {
        final var fr = FilterRequest.builder().with("custom").build();
        Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> {
            customs.findAll(null, fr, Pageable.unpaged());
        });
    }

    @Test
    public void canApplyCustomFilterWithParameter() {
        final var fr = FilterRequest.builder().with("custom", CustomFilter.Check.LESS.name()).build();
        final Page<CustomEntity> page = customs.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(Set.of(3L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }
}
