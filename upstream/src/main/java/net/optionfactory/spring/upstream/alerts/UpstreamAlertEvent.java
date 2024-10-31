package net.optionfactory.spring.upstream.alerts;

import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.springframework.lang.Nullable;

public record UpstreamAlertEvent(
        InvocationContext invocation,
        RequestContext request,
        @Nullable ResponseContext response,
        @Nullable ExceptionContext exception) {

}
