package net.optionfactory.spring.data.jpa.filtering.filters.spi;

/// A sort request rejected because of what the client sent: a sorter name that is not whitelisted.
///
/// [#sorter] and [#reason] describe the rejection in the terms of the client's own request, and are
/// safe to show to it. The message additionally names the entity, which is useful in a log but is
/// exactly what the name-based sorter contract keeps private, so it should not reach a client.
public class InvalidSortRequest extends IllegalArgumentException {

    /// The name of the rejected sorter, as the client requested it.
    public final String sorter;
    /// Why the request was rejected.
    public final String reason;

    /// @param sorterName the rejected sorter
    /// @param root the queried entity class, named in the message; `null` reads as `?`
    /// @param reason why the request was rejected, safe to show to the client
    public InvalidSortRequest(String sorterName, Class<?> root, String reason) {
        super(String.format("in sorter %s@%s: %s", sorterName, root == null ? "?" : root.getSimpleName(), reason));
        this.sorter = sorterName;
        this.reason = reason;
    }
}
