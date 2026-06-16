package net.optionfactory.spring.upstream.alerts.spooler;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.BodiesStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.HeadersStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.MultipartStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.RenderedRequest;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.RenderedResponse;

public class AlertBodiesFunctions {

    public RenderedRequest abbreviated(InvocationContext invocation, RequestContext request, int maxSize) {
        return invocation.rendering().render(
                request,
                MultipartStrategy.RENDER_PARTS,
                HeadersStrategy.CONTENT,
                BodiesStrategy.ABBREVIATED_REDACTED,
                "✂️",
                maxSize
        );
    }

    public RenderedResponse abbreviated(InvocationContext invocation, ResponseContext response, int maxSize) {
        return invocation.rendering().render(
                response,
                MultipartStrategy.RENDER_PARTS,
                HeadersStrategy.CONTENT,
                BodiesStrategy.ABBREVIATED_REDACTED,
                "✂️",
                maxSize
        );
    }
}
