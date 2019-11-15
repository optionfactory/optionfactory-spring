package net.optionfactory.data.jpa.filtering.filters.spi;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;

public interface Filters {

    public static void ensurePropertyExists(Annotation annotation, EntityType<?> entity, String property) {
        try {
            entity.getAttribute(property);
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterConfiguration(String.format("filter %s@%s references a non-existent property %s", annotation, entity.getJavaType().getSimpleName(), property));
        }
    }

    public static Class<?> ensurePropertyOfAnyType(Annotation annotation, EntityType<?> entity, String property, Class<?>... types) {
        try {
            final Attribute<?, ?> attribute = entity.getAttribute(property);
            for (Class<?> type : types) {
                if (type.isAssignableFrom(attribute.getJavaType())) {
                    return type;
                }                
            }
            throw new InvalidFilterConfiguration(String.format("filter %s@%s expects a property of type %s, got %s", annotation, entity.getJavaType().getSimpleName(), Arrays.toString(types), attribute.getJavaType().getSimpleName()));            
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterConfiguration(String.format("filter %s@%s references a non-existent property %s", annotation, entity.getJavaType().getSimpleName(), property));
        }
    }

    public static void ensure(boolean test, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(String.format(format, values));
        }
    }

    public static class InvalidFilterConfiguration extends IllegalStateException {

        public InvalidFilterConfiguration(String reason) {
            super(reason);
        }

    }

    public static class InvalidFilterRequest extends IllegalArgumentException {

        public InvalidFilterRequest(String reason) {
            super(reason);
        }

    }
}
