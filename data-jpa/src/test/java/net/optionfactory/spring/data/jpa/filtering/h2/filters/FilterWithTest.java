package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
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
public class FilterWithTest {

    public static class CustomFilter implements Filter {

        private final String name;

        public CustomFilter(Filterable annotation, EntityType<?> entity) {
            if (!RootAgg.class.isAssignableFrom(entity.getJavaType())) {
                throw new InvalidFilterConfiguration(annotation.name(), entity, String.format("Unsupported entity type for filter %s", annotation.filter().getSimpleName()));
            }
            this.name = annotation.name();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            Filters.ensure(values.length == 1, root, name, "Custom filter expects a single parameter, but %s were given: %s", values.length, Arrays.toString(values));
            final String check = values[0];
            Filters.ensure(Stream.of(Check.values()).map(Check::name).anyMatch(c -> Objects.equals(c, check)), root, name, "Unknown check for custom filter: %s", check);
            switch (Check.valueOf(check)) {
                case LESS:
                    return builder.lessThan(root.get("id"), root.get("x"));
                case GREATER:
                    return builder.greaterThan(root.get("id"), root.get("x"));
                case EQUAL:
                    return builder.equal(root.get("id"), root.get("x"));
                default:
                    throw new IllegalStateException("this case should be unreachable");
            }
        }

        public static enum Check {
            LESS, GREATER, EQUAL;
        }
    }

    @Entity
    @Filterable(name = "custom", filter = CustomFilter.class)
    public static class RootAgg {

        @Id
        public long id;
        public long x;
    }

    public interface CustomsRepository extends JpaRepository<RootAgg, Long>, WhitelistFilteringRepository<RootAgg> {
    }

    @Inject
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

    private static RootAgg custom(long id, long x) {
        final RootAgg custom = new RootAgg();
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
        final Page<RootAgg> page = customs.findAll(null, fr, Pageable.unpaged());
        Assertions.assertEquals(Set.of(3L, 5L), page.getContent().stream().map(a -> a.id).collect(Collectors.toSet()));
    }
}
