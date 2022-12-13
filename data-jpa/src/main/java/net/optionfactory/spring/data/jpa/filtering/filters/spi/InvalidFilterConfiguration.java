package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.metamodel.EntityType;
import java.lang.annotation.Annotation;

public class InvalidFilterConfiguration extends IllegalStateException {

    public InvalidFilterConfiguration(Annotation annotation, EntityType<?> entity, String reason) {
        super(String.format("in filter %s@%s: %s", annotation, entity.getJavaType().getSimpleName(), reason));
    }
}
