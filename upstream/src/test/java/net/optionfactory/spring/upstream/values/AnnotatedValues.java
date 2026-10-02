package net.optionfactory.spring.upstream.values;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpRequestExecution;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

public class AnnotatedValues {

    public static final Expressions EXPRESSIONS = new Expressions(null, null);

    public static final Map<Method, EndpointDescriptor> ENDPOINTS = Stream.of(AnnotatedValuesClient.class.getMethods())
            .map(m -> new EndpointDescriptor("up", m.getName(), m, null))
            .collect(Collectors.toMap(EndpointDescriptor::method, Function.identity()));

    public static InvocationContext invocation(String endpoint, Object... args) {
        final var descriptor = ENDPOINTS.values().stream().filter(e -> e.name().equals(endpoint)).findFirst().orElseThrow();
        return new InvocationContext(EXPRESSIONS, null, null, descriptor, args, "boot", 1, null, Buffering.BUFFERED);
    }

    public static RequestContext request(String uri) {
        final var headers = new HttpHeaders();
        headers.add("X-Static", "existing");
        return new RequestContext(Instant.EPOCH, HttpMethod.GET, URI.create(uri), headers, Map.of(), new byte[0]);
    }

    public static RequestContext intercept(UpstreamHttpInterceptor interceptor, InvocationContext invocation, RequestContext request) throws Exception {
        interceptor.preprocess(AnnotatedValuesClient.class, EXPRESSIONS, ENDPOINTS);
        final var executed = new AtomicReference<RequestContext>();
        final UpstreamHttpRequestExecution execution = (i, r) -> {
            executed.set(r);
            return new ResponseContext(Instant.EPOCH, HttpStatus.OK, "OK", new HttpHeaders(), ResponseContext.BodySource.of(new byte[0]), false);
        };
        interceptor.intercept(invocation, request, execution);
        return executed.get();
    }
}
