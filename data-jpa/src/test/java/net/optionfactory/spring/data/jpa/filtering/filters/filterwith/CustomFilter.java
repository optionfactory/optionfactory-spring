package net.optionfactory.spring.data.jpa.filtering.filters.filterwith;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;

public class CustomFilter implements Filter {

    private final String name;

    public CustomFilter(Filterable annotation, EntityType<?> entity) {
        if (!CustomEntity.class.isAssignableFrom(entity.getJavaType())) {
            throw new InvalidFilterConfiguration(annotation, entity, String.format("Unsupported entity type for filter %s", annotation.filter().getSimpleName()));
        }
        this.name = annotation.name();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
        Filters.ensure(values.length == 1, name, root, "Custom filter expects a single parameter, but %s were given: %s", values.length, Arrays.toString(values));
        final String check = values[0];
        Filters.ensure(Stream.of(Check.values()).map(Check::name).anyMatch(c -> Objects.equals(c, check)), name, root, "Unknown check for custom filter: %s", check);
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
