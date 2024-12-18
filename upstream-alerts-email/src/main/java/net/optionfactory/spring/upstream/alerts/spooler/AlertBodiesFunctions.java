package net.optionfactory.spring.upstream.alerts.spooler;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.rendering.BodyRendering;

public class AlertBodiesFunctions {
    
    public String abbreviated(InvocationContext invocation, RequestContext request, int maxSize) {
        return invocation.rendering().render(request, BodyRendering.Strategy.ABBREVIATED_REDACTED, "✂️", maxSize);
    }

    public String abbreviated(InvocationContext invocation, ResponseContext response, int maxSize) {
        return invocation.rendering().render(response, BodyRendering.Strategy.ABBREVIATED_REDACTED, "✂️", maxSize);
    }
}
