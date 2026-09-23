package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.criteria.Root;

/// A filter request rejected because of what the client sent: a filter name that is not
/// whitelisted, an operator outside the whitelist, a value that cannot be parsed.
///
/// [#filter] and [#reason] describe the rejection in the terms of the client's own request, and are
/// safe to show to it. The message additionally names the entity, which is useful in a log but is
/// exactly what the name-based filter contract keeps private, so it should not reach a client.
public class InvalidFilterRequest extends IllegalArgumentException {

    public final String filter;
    public final String reason;

    public InvalidFilterRequest(String filterName, Root<?> root, String reason) {
        super(String.format("in filter %s@%s: %s", filterName, root == null ? "?" : root.getJavaType().getSimpleName(), reason));
        this.filter = filterName;
        this.reason = reason;
    }
}
