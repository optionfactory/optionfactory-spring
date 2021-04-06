package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import javax.persistence.criteria.Root;

public class InvalidFilterRequest extends IllegalArgumentException {

    public InvalidFilterRequest(String filterName, Root<?> root, String reason) {
        super(String.format("in filter %s@%s: %s", filterName, root == null ? "?" : root.getJavaType().getSimpleName(), reason));
    }
}
