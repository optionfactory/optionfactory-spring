package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.metamodel.EntityType;

/// A sorter whitelisted with a configuration that cannot work: a path that does not resolve, or
/// that crosses a collection.
///
/// It is thrown when the repository is built, failing the application startup, and is a
/// developer's mistake: unlike an [InvalidSortRequest], it is not meant to reach a client.
public class InvalidSortConfiguration extends IllegalStateException {

    /// @param sorterName the name of the misconfigured sorter
    /// @param entity the entity the sorter is whitelisted on
    /// @param reason what is wrong
    public InvalidSortConfiguration(String sorterName, EntityType<?> entity, String reason) {
        super(String.format("in sorter %s@%s: %s", sorterName, entity.getJavaType().getSimpleName(), reason));
    }
}
