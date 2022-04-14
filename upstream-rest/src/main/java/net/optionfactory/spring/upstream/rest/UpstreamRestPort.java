package net.optionfactory.spring.upstream.rest;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.optionfactory.spring.upstream.UpstreamException;
import net.optionfactory.spring.upstream.UpstreamInterceptor;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ExchangeContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.PrepareContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.RequestContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ResponseContext;
import net.optionfactory.spring.upstream.UpstreamPort;
import net.optionfactory.spring.upstream.CompositeUpstreamResponseErrorHandler;
import net.optionfactory.spring.upstream.counters.UpstreamRequestCounter;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

public class UpstreamRestPort<CTX> implements UpstreamPort<CTX> {

    private final String upstreamId;
    private final UpstreamRequestCounter requestCounter;
    private final RestTemplate rest;
    private final List<UpstreamInterceptor<CTX>> interceptors;
    private final ThreadLocal<ExchangeContext<CTX>> callContexts = new ThreadLocal<>();

    public UpstreamRestPort(String upstreamId, UpstreamRequestCounter requestCounter, ObjectMapper objectMapper, SSLConnectionSocketFactory socketFactory, int connectionTimeoutInMillis, List<UpstreamInterceptor<CTX>> interceptors) {
        this(upstreamId, requestCounter, objectMapper, socketFactory, connectionTimeoutInMillis, interceptors, makeDefaultConverters(objectMapper));
    }

    public static List<HttpMessageConverter<?>> makeDefaultConverters(ObjectMapper objectMapper) {
        final var byteArrayMessageConverter = new ByteArrayHttpMessageConverter();
        final var mappingJacksonMessageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        final var formMessageConverter = new FormHttpMessageConverter();
        formMessageConverter.addPartConverter(mappingJacksonMessageConverter);
        final var resourceMessageConverter = new ResourceHttpMessageConverter();

        final var converters = Arrays.<HttpMessageConverter<?>>asList(
                byteArrayMessageConverter,
                formMessageConverter,
                mappingJacksonMessageConverter,
                resourceMessageConverter);
        return converters;
    }

    public UpstreamRestPort(String upstreamId, UpstreamRequestCounter requestCounter, ObjectMapper objectMapper, SSLConnectionSocketFactory socketFactory, int connectionTimeoutInMillis, List<UpstreamInterceptor<CTX>> interceptors, List<HttpMessageConverter<?>> converters) {
        final var builder = HttpClientBuilder.create();
        builder.setSSLSocketFactory(socketFactory);
        final var client = builder.setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(connectionTimeoutInMillis).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                .build();
        final var innerRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
        final var requestFactory = new BufferingClientHttpRequestFactory(innerRequestFactory);

        final var inner = new RestTemplate(requestFactory);
        inner.setMessageConverters(converters);
        inner.setInterceptors(List.of(new RestInterceptors<>(upstreamId, interceptors, callContexts)));
        inner.setErrorHandler(new CompositeUpstreamResponseErrorHandler<>(upstreamId, interceptors, callContexts));
        this.upstreamId = upstreamId;
        this.requestCounter = requestCounter;
        this.interceptors = interceptors;
        this.rest = inner;
    }

    @Override
    public <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity, Class<T> responseType, Hints<CTX> hints) {
        final ExchangeContext<CTX> ctx = new ExchangeContext<>();
        ctx.hints = hints;
        ctx.prepare = new UpstreamInterceptor.PrepareContext<>();
        ctx.prepare.requestId = requestCounter.next();
        ctx.prepare.ctx = context;
        ctx.prepare.endpointId = endpointId;
        ctx.prepare.entity = requestEntity;
        ctx.prepare.upstreamId = upstreamId;
        callContexts.set(ctx);
        try {
            ctx.prepare.entity = makeEntity(ctx.hints, ctx.prepare);
            final ResponseEntity<T> response = rest.exchange(ctx.prepare.entity, responseType);
            for (UpstreamInterceptor<CTX> interceptor : interceptors) {
                interceptor.mappingSuccess(ctx.hints, ctx.prepare, ctx.request, ctx.response, response);
            }
            return response;
        } finally {
            callContexts.remove();
        }
    }

    @Override
    public <T> ResponseEntity<T> exchange(CTX context, String endpointId, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType, Hints<CTX> hints) {
        final ExchangeContext<CTX> ctx = new ExchangeContext<>();
        ctx.hints = hints;
        ctx.prepare = new UpstreamInterceptor.PrepareContext<>();
        ctx.prepare.requestId = requestCounter.next();
        ctx.prepare.ctx = context;
        ctx.prepare.endpointId = endpointId;
        ctx.prepare.entity = requestEntity;
        ctx.prepare.upstreamId = upstreamId;
        callContexts.set(ctx);
        try {
            ctx.prepare.entity = makeEntity(ctx.hints, ctx.prepare);
            final ResponseEntity<T> response = rest.exchange(ctx.prepare.entity, responseType);
            for (UpstreamInterceptor<CTX> interceptor : interceptors) {
                interceptor.mappingSuccess(ctx.hints, ctx.prepare, ctx.request, ctx.response, response);
            }
            return response;
        } finally {
            callContexts.remove();
        }
    }

    private RequestEntity<?> makeEntity(Hints<CTX> hints, PrepareContext<CTX> prepare) {
        final var headers = new HttpHeaders();
        headers.addAll(prepare.entity.getHeaders());
        for (var interceptor : interceptors) {
            final var newHeaders = interceptor.prepare(hints, prepare);
            if (newHeaders != null) {
                headers.addAll(newHeaders);
            }
        }
        return new RequestEntity<>(prepare.entity.getBody(), headers, prepare.entity.getMethod(), prepare.entity.getUrl(), prepare.entity.getType());
    }

    public static class RestInterceptors<CTX> implements ClientHttpRequestInterceptor {

        private final String upstreamId;
        private final List<UpstreamInterceptor<CTX>> interceptors;
        private final ThreadLocal<ExchangeContext<CTX>> exchangeContexts;

        public RestInterceptors(String upstreamId, List<UpstreamInterceptor<CTX>> interceptors, ThreadLocal<ExchangeContext<CTX>> exchangeContexts) {
            this.upstreamId = upstreamId;
            this.interceptors = interceptors;
            this.exchangeContexts = exchangeContexts;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] requestBodyBytes, ClientHttpRequestExecution execution) throws IOException {
            final ExchangeContext<CTX> context = exchangeContexts.get();
            context.request = new RequestContext();
            context.request.at = Instant.now();
            context.request.body = new ByteArrayResource(requestBodyBytes);
            context.request.headers = request.getHeaders();
            for (var interceptor : interceptors) {
                interceptor.before(context.hints, context.prepare, context.request);
            }
            try {
                final ClientHttpResponse response = execution.execute(request, requestBodyBytes);
                try (final InputStream body = response.getBody()) {
                    context.response = new ResponseContext();
                    context.response.at = Instant.now();
                    context.response.status = response.getStatusCode();
                    context.response.headers = response.getHeaders();
                    context.response.body = new ByteArrayResource(StreamUtils.copyToByteArray(body));
                }
                for (var interceptor : interceptors) {
                    interceptor.remotingSuccess(context.hints, context.prepare, context.request, context.response);
                }
                return response;
            } catch (IOException | RuntimeException ex) {
                context.error = new UpstreamInterceptor.ErrorContext();
                context.error.at = Instant.now();
                searchCauseOfType(ex, JsonMappingException.class).ifPresent(cex -> {
                    context.error.ex = cex;
                    for (var interceptor : interceptors) {
                        interceptor.remotingError(context.hints, context.prepare, context.request, context.error);
                    }
                    throw new UpstreamException(upstreamId, "MAPPING_ERROR", cex.getMessage());
                });
                searchCauseOfType(ex, SocketException.class).ifPresent(cex -> {
                    context.error.ex = cex;
                    for (var interceptor : interceptors) {
                        interceptor.remotingError(context.hints, context.prepare, context.request, context.error);
                    }
                    throw new UpstreamException(upstreamId, "UPSTREAM_DOWN", cex.getMessage());
                });
                context.error.ex = ex;
                for (var interceptor : interceptors) {
                    interceptor.remotingError(context.hints, context.prepare, context.request, context.error);
                }
                throw new UpstreamException(upstreamId, "GENERIC_ERROR", ex.getMessage());
            }
        }

        private static <T> Optional<T> searchCauseOfType(Throwable specific, Class<T> type) {
            for (var current = specific; current != null; current = current.getCause()) {
                if (type.isAssignableFrom(current.getClass())) {
                    return Optional.of((T) current);
                }
            }
            return Optional.empty();
        }
    }
}
