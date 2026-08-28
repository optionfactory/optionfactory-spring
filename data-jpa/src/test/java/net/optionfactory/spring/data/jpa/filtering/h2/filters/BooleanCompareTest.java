package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class BooleanCompareTest {

    @Entity
    @BooleanCompare(name = "javaBoolean", path = "data")
    @BooleanCompare(name = "yesNoBoolean", path = "data", trueValue = "yes", falseValue = "no")
    @BooleanCompare(name = "YNMatchCaseBoolean", path = "data", trueValue = "Y", falseValue = "N")
    public static class Root {

        @Id
        public long id;
        public boolean data;
    }

    public interface FlagsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {
    }

    @Inject
    private FlagsRepository flags;

    @BeforeEach
    public void setup() {
        final Root trueFlag = new Root();
        trueFlag.id = 1;
        trueFlag.data = true;
        final Root falseFlag = new Root();
        falseFlag.id = 2;
        falseFlag.data = false;
        flags.saveAll(Arrays.asList(trueFlag, falseFlag));
    }

    @Test
    public void canFilterBooleanValueWithDefaultOptions() {
        Assertions.assertEquals(Set.of(1L), idsIn(flags.findAll(null, filter("javaBoolean", "true"), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(2L), idsIn(flags.findAll(null, filter("javaBoolean", "false"), Pageable.unpaged())));
    }

    @Test
    public void canFilterBooleanValueWithCustomValues() {
        Assertions.assertEquals(Set.of(1L), idsIn(flags.findAll(null, filter("YNMatchCaseBoolean", "Y"), Pageable.unpaged())));
        Assertions.assertEquals(Set.of(2L), idsIn(flags.findAll(null, filter("YNMatchCaseBoolean", "N"), Pageable.unpaged())));
    }

    @Test
    public void throwsWhenValueDoesNotMatch() {
        Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> {
            flags.findAll(null, filter("yesNoBoolean", "maybe"), Pageable.unpaged());
        });
    }

    private static FilterRequest filter(String filterName, String value) {
        return FilterRequest.builder()
                .bool(filterName, f -> f.eq(value))
                .build();
    }

    private static Set<Long> idsIn(Page<Root> page) {
        return page.getContent().stream().map(flag -> flag.id).collect(Collectors.toSet());
    }
}
