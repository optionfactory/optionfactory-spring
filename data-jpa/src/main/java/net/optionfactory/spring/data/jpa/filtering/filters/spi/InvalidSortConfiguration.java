package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.metamodel.EntityType;

public class InvalidSortConfiguration extends IllegalStateException {

    public InvalidSortConfiguration(String sorterName, EntityType<?> entity, String reason) {
        super(String.format("in sorter %s@%s: %s", sorterName, entity.getJavaType().getSimpleName(), reason));
    }
}
