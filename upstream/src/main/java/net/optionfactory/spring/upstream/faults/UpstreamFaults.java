package net.optionfactory.spring.upstream.faults;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.InvocationContext;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.Strategies.FaultOn4xxOr5xxPredicate;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.Strategies.FaultOnRemotingErrorPredicate;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.Strategies.OnRemotingError;
import net.optionfactory.spring.upstream.faults.UpstreamFaults.Strategies.OnRemotingSuccess;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
public @interface UpstreamFaults {

    Class<? extends OnRemotingSuccess> onRemotingSuccess() default FaultOn4xxOr5xxPredicate.class;

    Class<? extends OnRemotingError> onRemotingError() default FaultOnRemotingErrorPredicate.class;

    public record UpstreamFaultEvent(
            String bootId,
            String upstreamId,
            Object principal,
            String endpoint,
            Method method,
            Object[] arguments,
            long requestId,
            Instant requestedAt,
            URI requestUri,
            HttpMethod requestMethod,
            HttpHeaders requestHeaders,
            byte[] requestBody,
            Instant handledAt,
            HttpHeaders responseHeaders,
            HttpStatusCode responseStatus,
            byte[] responseBody,
            Exception exception) {

    }

    public static class Interceptor implements UpstreamHttpInterceptor {

        private final Map<Method, OnRemotingSuccess> remotingSuccessConfs = new ConcurrentHashMap<>();
        private final Map<Method, OnRemotingError> remotingErrorConfs = new ConcurrentHashMap<>();
        private final ApplicationEventPublisher publisher;

        public Interceptor(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
            final var defaultAnn = AnnotationUtils.synthesizeAnnotation(UpstreamFaults.class);
            final var interfaceAnn = Optional.ofNullable(k.getAnnotation(UpstreamFaults.class));
            for (Method m : k.getDeclaredMethods()) {
                final var ann = Optional.ofNullable(m.getAnnotation(UpstreamFaults.class))
                        .or(() -> interfaceAnn)
                        .orElse(defaultAnn);
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
                    publisher.publishEvent(new UpstreamFaultEvent(
                            ctx.boot(),
                            ctx.upstream(),
                            ctx.principal(),
                            ctx.endpoint(),
                            ctx.method(),
                            ctx.arguments(),
                            ctx.request(),
                            ctx.requestedAt(),
                            request.getURI(),
                            request.getMethod(),
                            request.getHeaders(),
                            body,
                            ctx.clock().instant(),
                            response.getHeaders(),
                            response.getStatusCode(),
                            StreamUtils.copyToByteArray(response.getBody()),
                            null));
                }
                return response;
            } catch (Exception ex) {
                if (remotingErrorConfs.get(ctx.method()).isFault(ctx, request, body, ex)) {
                    publisher.publishEvent(new UpstreamFaultEvent(
                            ctx.boot(),
                            ctx.upstream(),
                            ctx.principal(),
                            ctx.endpoint(),
                            ctx.method(),
                            ctx.arguments(),
                            ctx.request(),
                            ctx.requestedAt(),
                            request.getURI(),
                            request.getMethod(),
                            request.getHeaders(),
                            body,
                            ctx.clock().instant(),
                            null,
                            null,
                            null,
                            ex));
                }
                throw ex;
            }
        }

    }

    public interface Strategies {

        public static HttpStatusCode status(ClientHttpResponse response) {
            try {
                return response.getStatusCode();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public interface OnRemotingSuccess {

            boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response);

        }

        public static class OkOnRemotingSuccessPredicate implements OnRemotingSuccess {

            @Override
            public boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
                return false;
            }

        }

        public static class OkOn2xxPredicate implements OnRemotingSuccess {

            @Override
            public boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
                return !status(response).is2xxSuccessful();
            }
        }

        public static class FaultOn5xxPredicate implements OnRemotingSuccess {

            @Override
            public boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
                return status(response).is5xxServerError();
            }
        }

        public static class FaultOn4xxPredicate implements OnRemotingSuccess {

            @Override
            public boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
                return status(response).is4xxClientError();
            }
        }

        public static class FaultOn4xxOr5xxPredicate implements OnRemotingSuccess {

            @Override
            public boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpResponse response) {
                final HttpStatusCode status = status(response);
                return status.is4xxClientError() || status.is5xxServerError();
            }
        }

        public interface OnRemotingError {

            boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, Exception ex);
        }

        public static class OkOnRemotingErrorPredicate implements OnRemotingError {

            @Override
            public boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, Exception ex) {
                return false;
            }

        }

        public static class FaultOnRemotingErrorPredicate implements OnRemotingError {

            @Override
            public boolean isFault(InvocationContext ctx, HttpRequest request, byte[] body, Exception ex) {
                return true;
            }

        }

    }

}
