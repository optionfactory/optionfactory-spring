package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.*;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.persistence.criteria.From;
import javax.persistence.criteria.JoinType;

/**
 * Utility methods for defining filters.
 */
public interface Filters {

    public static final Pattern ATTRIBUTE_TRAVERSAL_PATTERN = Pattern.compile("(?:<(?<type>GET|INNER_JOIN|LEFT_JOIN)>)?(?<attribute>.+)");

    public static Traversal traversal(Annotation annotation, EntityType<?> entity, String pathTraversalSpec) {
        ManagedType<?> currentType = entity;
        Attribute<?, ?> currentAttribute = null;
        final var path = new ArrayList<AttributeTraversal>();
        for (String attributeTraversalSpec : pathTraversalSpec.split("\\.")) {
            final Matcher m = ATTRIBUTE_TRAVERSAL_PATTERN.matcher(attributeTraversalSpec);
            if (!m.matches()) {
                throw new InvalidFilterConfiguration(String.format("filter %s@%s uses an invalid attribute traversal spec: %s", annotation, entity.getJavaType().getSimpleName(), attributeTraversalSpec));
            }
            final String type = m.group("type");
            final String attributeName = m.group("attribute");
            if (currentType == null) {
                throw new InvalidFilterConfiguration(String.format("filter %s@%s references attribute %s in spec %s owned by the the non-managed type %s", annotation, entity.getJavaType().getSimpleName(), attributeName, pathTraversalSpec, currentAttribute.getJavaType().getSimpleName()));
            }
            try {
                currentAttribute = currentType.getAttribute(attributeName);
            } catch (IllegalArgumentException exception) {
                throw new InvalidFilterConfiguration(String.format("filter %s@%s references a non-existent property %s.%s in spec %s", annotation, entity.getJavaType().getSimpleName(), currentType.getJavaType().getSimpleName(), attributeName, pathTraversalSpec));
            }
            if (currentAttribute instanceof SingularAttribute) {
                final AttributeTraversal candidate = new AttributeTraversal(attributeName, Optional.ofNullable(type).map(TraversalType::valueOf).orElse(TraversalType.GET));
                ensure(TraversalType.GET == candidate.type, "filter %s@%s uses an invalid traversal type '%s' for singular attribute '%s' in spec: '%s'", annotation, entity.getJavaType().getSimpleName(), candidate.type, candidate.name, pathTraversalSpec);
                path.add(candidate);
                final Type targetType = ((SingularAttribute) currentAttribute).getType();
                if (targetType instanceof ManagedType) {
                    currentType = (ManagedType<?>) targetType;
                } else {
                    currentType = null;
                }
                continue;
            }
            if (currentAttribute instanceof PluralAttribute) {
                final AttributeTraversal candidate = new AttributeTraversal(attributeName, Optional.ofNullable(type).map(TraversalType::valueOf).orElse(TraversalType.INNER_JOIN));
                ensure(EnumSet.of(TraversalType.INNER_JOIN, TraversalType.LEFT_JOIN).contains(candidate.type), "filter %s@%s uses an invalid traversal type '%s' for plural attribute '%s' in spec: '%s'", annotation, entity.getJavaType().getSimpleName(), candidate.type, candidate.name, pathTraversalSpec);
                path.add(candidate);
                final Type targetType = ((PluralAttribute) currentAttribute).getElementType();
                if (targetType instanceof ManagedType) {
                    currentType = (ManagedType<?>) targetType;
                } else {
                    currentType = null;
                }
                continue;
            }
            throw new InvalidFilterConfiguration(String.format("filter %s@%s references attribute %s in spec %s which is neither a SingularAttribute nor a PluralAttribute", annotation, entity.getJavaType().getSimpleName(), attributeName, pathTraversalSpec));
        }
        return new Traversal(path, currentAttribute);
    }

    public static Class<?> ensurePropertyOfAnyType(Annotation annotation, EntityType<?> entity, Traversal traversal, Class<?>... types) {
        final Class<?> javaType = traversal.attribute.getJavaType();
        return Stream.of(types)
                .filter(type -> type.isAssignableFrom(javaType))
                .findFirst()
                .orElseThrow(() -> new InvalidFilterConfiguration(String.format("filter %s@%s expects traversal %s to be of type %s, got %s", annotation, entity.getJavaType().getSimpleName(), traversal.path, List.of(types), traversal.attribute.getJavaType().getSimpleName())));
    }

    public static void ensure(boolean test, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(String.format(format, values));
        }
    }

    private static Path<?> join(Path<?> root, String attribute, JoinType jt) {
        return ((From<?, ?>) root).join(attribute, jt);
    }

    public static <T> Path<T> path(Root<?> root, Traversal traversal) {
        Path<?> path = root;
        for (AttributeTraversal part : traversal.path) {
            try {
                switch (part.type) {
                    case GET:
                        path = path.get(part.name);
                        break;
                    case INNER_JOIN:
                        path = join(path, part.name, JoinType.INNER);
                        break;
                    case LEFT_JOIN:
                        path = join(path, part.name, JoinType.LEFT);
                        break;
                    default:
                        throw new IllegalArgumentException(String.format("Unsupported TraversalType: %s", part.type));
                }
            } catch (IllegalArgumentException exception) {
                throw new InvalidFilterConfiguration(String.format("attributes chain %s from entity %s references a non-existent attribute %s.%s", traversal, root.getJavaType().getSimpleName(), path.getJavaType().getSimpleName(), part));
            }
        }
        return (Path<T>) path;
    }

    public enum TraversalType {
        LEFT_JOIN,
        INNER_JOIN,
        GET;
    }

    public static class Traversal {

        public final List<AttributeTraversal> path;
        public final Attribute<?, ?> attribute;

        public Traversal(List<AttributeTraversal> path, Attribute<?, ?> attribute) {
            this.path = path;
            this.attribute = attribute;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", path, attribute.getJavaType());
        }

    }

    public static class AttributeTraversal {

        public final String name;
        public final TraversalType type;

        public AttributeTraversal(String name, TraversalType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", name, type);
        }

    }

}
