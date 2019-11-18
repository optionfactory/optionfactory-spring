package net.optionfactory.data.jpa.filtering.filters.spi;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.Type;

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
            final List<String> propertyChain = Arrays.asList(property.split("\\."));
            final Attribute<?, ?> attribute = getAttributeFromPropertyChain(annotation, entity, propertyChain);
            for (Class<?> type : types) {
                if (type.isAssignableFrom(attribute.getJavaType())) {
                    return type;
                }
            }
            throw new InvalidFilterConfiguration(String.format("filter %s@%s expects property %s of type %s, got %s", annotation, entity.getJavaType().getSimpleName(), property, Arrays.toString(types), attribute.getJavaType().getSimpleName()));
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterConfiguration(String.format("filter %s@%s references a non-existent property '%s'", annotation, entity.getJavaType().getSimpleName(), property));
        }
    }

    private static Attribute<?, ?> getAttributeFromPropertyChain(Annotation annotation, EntityType<?> entity, List<String> propertyChain) {
        ManagedType<?> reachedType = entity;
        Attribute<?, ?> reachedAttribute = null;
        for (String property : propertyChain) {
            if (reachedType == null) {
                throw new InvalidFilterConfiguration(String.format("filter %s@%s references property %s in property chain %s owned by the the non-managed type %s", annotation, entity.getJavaType().getSimpleName(), property, propertyChain, reachedAttribute.getJavaType().getSimpleName()));
            }
            reachedAttribute = reachedType.getAttribute(property);
            if (reachedAttribute instanceof SingularAttribute) {
                final Type targetType = ((SingularAttribute) reachedAttribute).getType();
                if (targetType instanceof ManagedType) {
                    reachedType = (ManagedType<?>) targetType;
                } else {
                    reachedType = null;
                }
            } else {
                throw new InvalidFilterConfiguration(String.format("filter %s@%s references property %s in property chain %s which is not a SingolarAttribute (the only case currently supported)", annotation, entity.getJavaType().getSimpleName(), property, propertyChain));
            }
        }
        return reachedAttribute;
    }

    public static void ensure(boolean test, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(String.format(format, values));
        }
    }

    public static <T> Path<T> traverseProperty(Root<?> root, String property) {
        final List<String> propertyChain = Arrays.asList(property.split("\\."));
        Path<?> path = root;
        for (String part : propertyChain) {
            path = path.get(part);
        }
        return (Path<T>) path;
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
