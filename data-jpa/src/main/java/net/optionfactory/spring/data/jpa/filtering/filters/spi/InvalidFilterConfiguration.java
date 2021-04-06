package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import java.lang.annotation.Annotation;
import javax.persistence.metamodel.EntityType;

public class InvalidFilterConfiguration extends IllegalStateException {

    public InvalidFilterConfiguration(Annotation annotation, EntityType<?> entity, String reason) {
        super(String.format("in filter %s@%s: %s", annotation, entity.getJavaType().getSimpleName(), reason));
    }
}
