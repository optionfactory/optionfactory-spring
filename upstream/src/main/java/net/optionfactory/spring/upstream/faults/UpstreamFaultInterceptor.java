package net.optionfactory.spring.upstream.faults;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.Upstream.Faults;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

public class UpstreamFaultInterceptor implements UpstreamHttpInterceptor {

    private final Map<Method, UpstreamFaultStrategies.OnRemotingSuccess> remotingSuccessConfs = new ConcurrentHashMap<>();
    private final Map<Method, UpstreamFaultStrategies.OnRemotingError> remotingErrorConfs = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher publisher;

    public UpstreamFaultInterceptor(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        final var defaultAnn = AnnotationUtils.synthesizeAnnotation(Upstream.Faults.class);
        final var interfaceAnn = Optional.ofNullable(k.getAnnotation(Faults.class));
        for (Method m : k.getDeclaredMethods()) {
            final var ann = Optional.ofNullable(m.getAnnotation(Upstream.Faults.class)).or(() -> interfaceAnn).orElse(defaultAnn);
            remotingSuccessConfs.put(m, make(ann.onRemotingSuccess()));
            remotingErrorConfs.put(m, make(ann.onRemotingError()));
        }
    }

    public static <T> T make(Class<T> k) {
        try {
            return k.getConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            final ClientHttpResponse response = execution.execute(request, body);
            if (remotingSuccessConfs.get(ctx.method()).isFault(ctx, request, body, response)) {
                publisher.publishEvent(new UpstreamFaultEvent(ctx.boot(), ctx.upstream(), ctx.principal(), ctx.endpoint(), ctx.method(), ctx.arguments(), ctx.request(), ctx.requestedAt(), request.getURI(), request.getMethod(), request.getHeaders(), body, ctx.clock().instant(), response.getHeaders(), response.getStatusCode(), StreamUtils.copyToByteArray(response.getBody()), null));
            }
            return response;
        } catch (Exception ex) {
            if (remotingErrorConfs.get(ctx.method()).isFault(ctx, request, body, ex)) {
                publisher.publishEvent(new UpstreamFaultEvent(ctx.boot(), ctx.upstream(), ctx.principal(), ctx.endpoint(), ctx.method(), ctx.arguments(), ctx.request(), ctx.requestedAt(), request.getURI(), request.getMethod(), request.getHeaders(), body, ctx.clock().instant(), null, null, null, ex));
            }
            throw ex;
        }
    }

}
