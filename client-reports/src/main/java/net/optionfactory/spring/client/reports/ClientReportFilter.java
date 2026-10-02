package net.optionfactory.spring.client.reports;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// Receives reports that browsers POST about themselves (e.g. javascript errors), and turns each
/// into an application event, optionally logged.
///
/// A `POST` whose path, with the context path stripped, equals the configured uri is a report: its
/// body is parsed as json, an event is built from it and the current principal and published, and
/// the request is answered with `202 Accepted` without reaching the rest of the chain. The uri is
/// matched exactly, so `/client-errors` does not match `/client-errors/`. Every other request
/// proceeds down the chain untouched.
///
/// Reports are untrusted input, accepted from anyone, so the body is read up to `maxBodySize`
/// bytes only. A body that is larger, empty or not json is reported as the text node
/// `"unparseable report"` rather than rejected: the client is never told its report was refused.
///
/// The principal is whatever the `SecurityContextHolder` holds when the filter runs, `null` when
/// there is no authentication. Where it runs is up to the application: see
/// [net.optionfactory.spring.client.reports.errors.ClientErrors] for a ready-made registration.
///
/// When logging is enabled, each report is logged at WARN by a logger named after the filter,
/// as `[op:<name>]<rendered principal> <json>`.
///
/// @param <ET> the type of the published events
public class ClientReportFilter<ET> extends OncePerRequestFilter {

    private final Logger reportLogger;
    private final String name;
    private final String reportUri;
    private final ApplicationEventPublisher publisher;
    private final int maxBodySize;
    private final boolean log;
    private final BiFunction<Object, JsonNode, ET> eventFactory;
    private final Function<Object, String> principalRenderer;
    private final JsonMapper mapper = new JsonMapper();

    /// @param name names the logger and appears in each log line
    /// @param reportUri the path reports are posted to, relative to the context path
    /// @param publisher receives the events, synchronously on the request thread
    /// @param maxBodySize the number of body bytes read, beyond which the report is unparseable
    /// @param log whether each report is also logged
    /// @param eventFactory builds the event from the principal (possibly `null`) and the report
    /// @param principalRenderer renders the principal (possibly `null`) for the log line, only
    /// invoked when logging is enabled
    public ClientReportFilter(String name, String reportUri, ApplicationEventPublisher publisher, int maxBodySize, boolean log,
            BiFunction<Object, JsonNode, ET> eventFactory,
            Function<Object, String> principalRenderer) {
        this.name = name;
        this.reportLogger = LoggerFactory.getLogger(name);
        this.reportUri = reportUri;
        this.publisher = publisher;
        this.maxBodySize = maxBodySize;
        this.log = log;
        this.eventFactory = eventFactory;
        this.principalRenderer = principalRenderer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final var requestPath = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && reportUri.equals(requestPath)) {
            final var auth = SecurityContextHolder.getContext().getAuthentication();
            final var principal = auth == null ? null : auth.getPrincipal();
            final var json = bodyToJson(request);
            publisher.publishEvent(eventFactory.apply(principal, json));
            if (log) {
                reportLogger.warn(String.format("[op:%s]%s %s", name, principalRenderer.apply(principal), json.toString()));
            }
            response.setStatus(HttpStatus.ACCEPTED.value());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private JsonNode bodyToJson(HttpServletRequest req) {
        try (final var is = req.getInputStream()) {
            return mapper.readValue(is.readNBytes(maxBodySize), JsonNode.class);
        } catch (IOException | JacksonException ex) {
            return mapper.getNodeFactory().stringNode("unparseable report");
        }
    }

}
