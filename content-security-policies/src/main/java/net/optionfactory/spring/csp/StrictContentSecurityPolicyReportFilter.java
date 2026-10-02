package net.optionfactory.spring.csp;

import java.util.function.Function;
import net.optionfactory.spring.client.reports.ClientReportFilter;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyReportFilter.CspViolation;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;

/// Receives the violation reports browsers post to the report uri: a `POST` whose path, without the
/// context path, equals the report uri is answered with `202 Accepted` and not passed down the chain;
/// every other request proceeds.
///
/// Each report is published as a [CspViolation] event and, when logging is enabled, logged at WARN
/// on the `csp-violation` logger. The body is read up to the maximum size and parsed as json: a
/// larger or malformed one is recorded as the json string `"unparseable report"`, never failing the
/// request.
public class StrictContentSecurityPolicyReportFilter extends ClientReportFilter<CspViolation> {

    /// @param reportUri the path reports are posted to
    /// @param publisher publishes the [CspViolation] events
    /// @param maxBodySize the bytes of a report body read at most
    /// @param log whether reports are logged at WARN
    /// @param principalRenderer renders the reporting request's principal, `null` when it is
    /// unauthenticated, in the log line
    public StrictContentSecurityPolicyReportFilter(String reportUri, ApplicationEventPublisher publisher, int maxBodySize, boolean log, Function<Object, String> principalRenderer) {
        super("csp-violation", reportUri, publisher, maxBodySize, log, CspViolation::new, principalRenderer);
    }

    /// A violation report, as published to the application's listeners.
    ///
    /// The report is sent by the browser on its own: it is unauthenticated data, possibly forged, and
    /// should be treated as such.
    ///
    /// @param principal the principal of the request posting the report, `null` when
    /// unauthenticated
    /// @param content the report body, or the json string `"unparseable report"`
    public record CspViolation(Object principal, JsonNode content) {

    }

}
