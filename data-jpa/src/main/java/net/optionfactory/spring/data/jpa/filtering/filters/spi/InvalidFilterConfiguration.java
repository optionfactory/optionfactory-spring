package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.metamodel.EntityType;

public class InvalidFilterConfiguration extends IllegalStateException {

    public InvalidFilterConfiguration(String filterName, EntityType<?> entity, String reason) {
        super(String.format("in filter %s@%s: %s", filterName, entity.getJavaType().getSimpleName(), reason));
    }
}
