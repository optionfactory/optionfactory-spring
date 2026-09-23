package net.optionfactory.spring.data.jpa.filtering.filters.spi;

/// A sort request rejected because of what the client sent: a sorter name that is not whitelisted.
///
/// [#sorter] and [#reason] describe the rejection in the terms of the client's own request, and are
/// safe to show to it. The message additionally names the entity, which is useful in a log but is
/// exactly what the name-based sorter contract keeps private, so it should not reach a client.
public class InvalidSortRequest extends IllegalArgumentException {

    public final String sorter;
    public final String reason;

    public InvalidSortRequest(String sorterName, Class<?> root, String reason) {
        super(String.format("in sorter %s@%s: %s", sorterName, root == null ? "?" : root.getSimpleName(), reason));
        this.sorter = sorterName;
        this.reason = reason;
    }
}
