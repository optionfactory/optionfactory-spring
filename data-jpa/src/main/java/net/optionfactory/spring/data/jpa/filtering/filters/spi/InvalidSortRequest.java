package net.optionfactory.spring.data.jpa.filtering.filters.spi;

public class InvalidSortRequest extends IllegalArgumentException {

    public InvalidSortRequest(String sorterName, Class<?> root, String reason) {
        super(String.format("in sorter %s@%s: %s", sorterName, root == null ? "?" : root.getSimpleName(), reason));
    }
}
