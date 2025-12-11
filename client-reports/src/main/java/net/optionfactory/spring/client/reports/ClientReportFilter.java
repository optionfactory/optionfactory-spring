package net.optionfactory.spring.client.reports;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
        if ("POST".equals(request.getMethod()) && reportUri.equals(request.getRequestURI())) {
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
        try (final var is = req.getInputStream(); final var baos = new ByteArrayOutputStream()) {
            StreamUtils.copyRange(is, baos, 0, maxBodySize);
            return mapper.readValue(baos.toByteArray(), JsonNode.class);
        } catch (IOException ex) {
            return mapper.getNodeFactory().stringNode("unparseable report");
        }
    }

}
