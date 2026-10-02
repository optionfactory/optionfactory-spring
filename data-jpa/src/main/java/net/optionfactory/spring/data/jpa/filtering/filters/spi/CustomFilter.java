package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable;

/// A convenient base class for the custom filters whitelisted with [Filterable], holding the
/// name the filter is whitelisted under.
///
/// A subclass checks its preconditions on the [EntityType] in its constructor, throwing an
/// [InvalidFilterConfiguration], directly or through [Filters#ensureConfiguration], so that a
/// misconfigured filter fails the startup; and checks the values a client sent in
/// [Filter#toPredicate], throwing an [InvalidFilterRequest], directly or through [Filters#ensure].
///
/// ```java
/// public class ByOwnerInitial extends CustomFilter {
///
///     public ByOwnerInitial(Filterable annotation, EntityType<?> entity) {
///         super(annotation);
///         Filters.ensureConfiguration(entity.getJavaType() == Pet.class, name(), entity, "only applies to Pet");
///     }
///
///     @Override
///     public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
///         Filters.ensure(values.length == 1 && values[0] != null, root, name(), "expected an initial");
///         final Path<String> ownerName = root.get("owner").get("name");
///         return builder.like(ownerName, TextCompareFilter.escapeForLike(values[0]) + "%", '\\');
///     }
/// }
/// ```
public abstract class CustomFilter implements Filter {

    private final String name;

    /// @param annotation the whitelisting annotation, whose name the filter takes
    public CustomFilter(Filterable annotation) {
        name = annotation.name();
    }

    /// @return the name of the whitelisting annotation
    @Override
    public String name() {
        return name;
    }
}
