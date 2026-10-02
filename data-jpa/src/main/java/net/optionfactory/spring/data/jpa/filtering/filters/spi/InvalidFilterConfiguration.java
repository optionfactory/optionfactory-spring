package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.metamodel.EntityType;

/// A filter whitelisted with a configuration that cannot work: a path that does not resolve or
/// leads to a property of the wrong type, a quantifier that does not fit the path, conflicting join
/// types, an unsupported database.
///
/// It is thrown when the repository is built, failing the application startup, and is a
/// developer's mistake: unlike an [InvalidFilterRequest], it is not meant to reach a client.
public class InvalidFilterConfiguration extends IllegalStateException {

    /// @param filterName the name of the misconfigured filter
    /// @param entity the entity the filter is whitelisted on
    /// @param reason what is wrong
    public InvalidFilterConfiguration(String filterName, EntityType<?> entity, String reason) {
        super(String.format("in filter %s@%s: %s", filterName, entity.getJavaType().getSimpleName(), reason));
    }
}
