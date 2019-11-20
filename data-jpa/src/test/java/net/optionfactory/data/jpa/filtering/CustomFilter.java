package net.optionfactory.data.jpa.filtering;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import net.optionfactory.data.jpa.filtering.filters.FilterWith;
import net.optionfactory.data.jpa.filtering.filters.spi.Filters;

public class CustomFilter implements Filter {

    private final String name;

    public CustomFilter(FilterWith annotation, EntityType<?> entityType) {
        if (!CustomEntity.class.isAssignableFrom(entityType.getJavaType())) {
            throw new Filters.InvalidFilterConfiguration(String.format("Unsupported entity type %s for filter %s", entityType.getJavaType().getSimpleName(), annotation.filter().getSimpleName()));
        }
        this.name = annotation.name();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Predicate toPredicate(CriteriaBuilder builder, Root<?> root, String[] values) {
        Filters.ensure(values.length == 1, "Custom filter expects a single parameter, but %s were given: %s", values.length, Arrays.toString(values));
        final String check = values[0];
        Filters.ensure(Stream.of(Check.values()).map(Check::name).anyMatch(c -> Objects.equals(c, check)), "Unknown check for custom filter: %s", check);
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
