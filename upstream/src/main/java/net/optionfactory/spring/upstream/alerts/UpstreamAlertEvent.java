package net.optionfactory.spring.upstream.alerts;

import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import org.jspecify.annotations.Nullable;

/// An alert raised by an upstream client, published to the `ApplicationEventPublisher` configured with
/// [net.optionfactory.spring.upstream.UpstreamBuilder#publisher].
///
/// Exactly one of `response` and `exception` is set for the alerts raised by
/// `@Upstream.AlertOnResponse` (the response) and `@Upstream.AlertOnRemotingError` (the exception).
/// An alert raised because a response could not be mapped to the method return type carries the
/// mapping failure as `exception`, and the response as well when one was received.
///
/// The response is detached from the connection: its body has been read into memory, or replaced by
/// `<unavailable>` for the streamed endpoints, so the event can be handled after the call returned,
/// e.g. by an asynchronous listener.
///
/// ```java
/// @EventListener
/// public void on(UpstreamAlertEvent alert) {
///     notifier.notify(alert.invocation().endpoint().upstream(), alert.invocation().endpoint().name());
/// }
/// ```
///
/// @param invocation the invocation that raised the alert
/// @param request the request sent
/// @param response the response received, `null` for a remoting error
/// @param exception the failure, `null` for a response alert
public record UpstreamAlertEvent(
        InvocationContext invocation,
        RequestContext request,
        @Nullable ResponseContext response,
        @Nullable ExceptionContext exception) {

}
