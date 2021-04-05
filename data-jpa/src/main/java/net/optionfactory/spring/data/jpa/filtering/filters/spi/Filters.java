package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.*;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.criteria.From;
import javax.persistence.criteria.JoinType;

/**
 * Utility methods for defining filters.
 */
public interface Filters {

    public static void ensurePropertyExists(Annotation annotation, EntityType<?> entity, String pathTraversalSpec) {
        final List<Traversal> traversals = Traversal.parseAll(pathTraversalSpec);
        getAttributeFromPropertyChain(annotation, entity, traversals);
    }

    public static Class<?> ensurePropertyOfAnyType(Annotation annotation, EntityType<?> entity, String pathTraversalSpec, Class<?>... types) {
        final List<Traversal> traversals = Traversal.parseAll(pathTraversalSpec);
        final Attribute<?, ?> attribute = getAttributeFromPropertyChain(annotation, entity, traversals);
        try {
            for (Class<?> type : types) {
                if (type.isAssignableFrom(attribute.getJavaType())) {
                    return attribute.getJavaType();
                }
            }
            throw new InvalidFilterConfiguration(String.format("filter %s@%s expects traversal %s of type %s, got %s", annotation, entity.getJavaType().getSimpleName(), traversals, Arrays.toString(types), attribute.getJavaType().getSimpleName()));
        } catch (IllegalArgumentException ex) {
            throw new InvalidFilterConfiguration(String.format("filter %s@%s references a non-existent property '%s'", annotation, entity.getJavaType().getSimpleName(), traversals));
        }
    }

    private static Attribute<?, ?> getAttributeFromPropertyChain(Annotation annotation, EntityType<?> entity, List<Traversal> traversals) {
        ManagedType<?> reachedType = entity;
        Attribute<?, ?> reachedAttribute = null;
        for (Traversal traversal : traversals) {
            if (reachedType == null) {
                throw new InvalidFilterConfiguration(String.format("filter %s@%s references property %s in property chain %s owned by the the non-managed type %s", annotation, entity.getJavaType().getSimpleName(), traversal, traversals, reachedAttribute.getJavaType().getSimpleName()));
            }
            try {
                reachedAttribute = reachedType.getAttribute(traversal.attribute);
            } catch (IllegalArgumentException exception) {
                throw new InvalidFilterConfiguration(String.format("filter %s@%s references a non-existent property %s.%s in property chain %s", annotation, entity.getJavaType().getSimpleName(), reachedType.getJavaType().getSimpleName(), traversal, traversals));
            }
            if (reachedAttribute instanceof SingularAttribute) {
                final Type targetType = ((SingularAttribute) reachedAttribute).getType();
                if (targetType instanceof ManagedType) {
                    reachedType = (ManagedType<?>) targetType;
                } else {
                    reachedType = null;
                }
                continue;
            }
            if (reachedAttribute instanceof PluralAttribute) {
                ensure(EnumSet.of(TraversalType.JOIN_INNER, TraversalType.JOIN_LEFT, TraversalType.JOIN_RIGHT).contains(traversal.type), "in traversal: %s, plural attribute %s found with traveral type %s", traversals, traversal.attribute, traversal.type);
                final Type targetType = ((PluralAttribute) reachedAttribute).getElementType();
                if (targetType instanceof ManagedType) {
                    reachedType = (ManagedType<?>) targetType;
                } else {
                    reachedType = null;
                }
                continue;
            }
            throw new InvalidFilterConfiguration(String.format("filter %s@%s references property %s in property chain %s which is not a SingolarAttribute (the only case currently supported)", annotation, entity.getJavaType().getSimpleName(), traversal, traversals));
        }
        return reachedAttribute;
    }

    public static void ensure(boolean test, String format, Object... values) {
        if (!test) {
            throw new InvalidFilterRequest(String.format(format, values));
        }
    }

    public static <T> Path<T> traverseProperty(Root<?> root, String pathTraversalSpec) {
        final List<Traversal> chain = Traversal.parseAll(pathTraversalSpec);
        Path<?> path = root;
        for (Traversal part : chain) {
            try {
                path = part.traverse(path);
            } catch (IllegalArgumentException exception) {
                throw new InvalidFilterConfiguration(String.format("property chain %s from entity %s references a non-existent property %s.%s", chain, root.getJavaType().getSimpleName(), path.getJavaType().getSimpleName(), part));
            }
        }
        return (Path<T>) path;
    }

    public enum TraversalType {
        JOIN_LEFT,
        JOIN_INNER,
        JOIN_RIGHT,
        GET;
    }

    public static class Traversal {

        public final String attribute;
        public final TraversalType type;

        public Traversal(String attribute, TraversalType type) {
            this.attribute = attribute;
            this.type = type;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", attribute, type);
        }

        private static final Pattern PATTERN = Pattern.compile("(?:<(?<type>GET|JOIN_INNER|JOIN_LEFT|JOIN_RIGHT)>)?(?<attribute>.+)");

        public static Traversal parse(String attributeTraversalSpec) {
            final Matcher m = PATTERN.matcher(attributeTraversalSpec);
            if (!m.matches()) {
                throw new InvalidFilterConfiguration(String.format("invalid attribute traversal spec: %s", attributeTraversalSpec));
            }
            final TraversalType type = Optional.ofNullable(m.group("type")).map(TraversalType::valueOf).orElse(TraversalType.GET);
            final String attributre = m.group("attribute");
            return new Traversal(attributre, type);
        }

        public static List<Traversal> parseAll(String pathTraversalSpec) {
            return Stream.of(pathTraversalSpec.split("\\."))
                    .map(Traversal::parse)
                    .collect(Collectors.toList());
        }

        public Path<?> traverse(Path<?> root) {
            switch (type) {
                case GET:
                    return root.get(attribute);
                case JOIN_INNER:
                    return join(root, attribute, JoinType.INNER);
                case JOIN_LEFT:
                    return join(root, attribute, JoinType.LEFT);
                case JOIN_RIGHT:
                    return join(root, attribute, JoinType.RIGHT);
                default:
                    throw new IllegalArgumentException(String.format("Unsupported TraversalType: %s", this));
            }
        }

        private static Path<?> join(Path<?> root, String attribute, JoinType jt) {
            return ((From<?, ?>) root).join(attribute, jt);
        }
    }

}
