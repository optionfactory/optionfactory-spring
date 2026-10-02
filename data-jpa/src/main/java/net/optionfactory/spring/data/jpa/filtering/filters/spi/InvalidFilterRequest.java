package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.Root;

/// A filter request rejected because of what the client sent: a filter name that is not
/// whitelisted, an operator outside the whitelist, a value that cannot be parsed. It also rejects a
/// whole request that cannot be read, [#filter] then naming the parameter that carried it.
///
/// [#filter] and [#reason] describe the rejection in the terms of the client's own request, and are
/// safe to show to it. The message additionally names the entity, which is useful in a log but is
/// exactly what the name-based filter contract keeps private, so it should not reach a client.
public class InvalidFilterRequest extends IllegalArgumentException {

    /// The name of the rejected filter, as the client requested it, or the name of the parameter
    /// that carried an unreadable request.
    public final String filter;
    /// Why the request was rejected.
    public final String reason;

    /// @param filterName the rejected filter, or the parameter carrying an unreadable request
    /// @param root the query root, naming the entity in the message; `null` when there is no query
    /// yet, the entity then reading as `?`
    /// @param reason why the request was rejected, safe to show to the client
    public InvalidFilterRequest(String filterName, Root<?> root, String reason) {
        super(String.format("in filter %s@%s: %s", filterName, root == null ? "?" : root.getJavaType().getSimpleName(), reason));
        this.filter = filterName;
        this.reason = reason;
    }
}
