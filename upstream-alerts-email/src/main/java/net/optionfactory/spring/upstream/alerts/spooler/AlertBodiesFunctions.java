package net.optionfactory.spring.upstream.alerts.spooler;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.BodiesStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.HeadersStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.MultipartStrategy;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.RenderedRequest;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.RenderedResponse;

/// The `#bodies` expression object of the alert layout, rendering the request and the response of
/// an alert in a form fit for an email.
///
/// Rendering goes through the [net.optionfactory.spring.upstream.rendering.PayloadsRendering] of
/// the alert's own invocation, so the redactions configured for the upstream apply to the email
/// too: headers are redacted, multipart payloads are rendered part by part, binary bodies are
/// replaced by their size, and textual bodies are redacted then abbreviated around a `✂️`.
///
/// [AlertsEmailsSpooler#templateEngine] registers it; an engine built otherwise needs
/// `new SingletonDialect("bodies", new AlertBodiesFunctions())` for the layout to render. The
/// layout calls it as `${#bodies.abbreviated(alert.invocation, alert.request, 2048)}`.
///
/// Instances are stateless.
public class AlertBodiesFunctions {

    /// @param invocation the alert's invocation, whose rendering configuration is used
    /// @param request the request to render
    /// @param maxSize the maximum length of each rendered body, in chars, the `✂️` included
    /// @return the redacted uri, headers and abbreviated body, with one rendered part per part of
    /// a multipart body
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

    /// @param invocation the alert's invocation, whose rendering configuration is used
    /// @param response the response to render, not `null`: an alert raised by an exception has
    /// none
    /// @param maxSize the maximum length of each rendered body, in chars, the `✂️` included
    /// @return the redacted headers and abbreviated body, with one rendered part per part of a
    /// multipart body
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
