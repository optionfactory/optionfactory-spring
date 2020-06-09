package net.optionfactory.spring.upstream;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

public class UpstreamRestPort<CONTEXT> {

    private final String upstreamId;
    private final RestTemplate rest;
    private final List<UpstreamInterceptor> interceptors;

    public UpstreamRestPort(String upstreamId, ObjectMapper objectMapper, SSLConnectionSocketFactory socketFactory, int connectionTimeoutInMillis, List<UpstreamInterceptor> interceptors) {
        final var builder = HttpClientBuilder.create();
        builder.setSSLSocketFactory(socketFactory);
        final var client = builder.setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(connectionTimeoutInMillis).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                .build();
        final var innerRequestFactory = new HttpComponentsClientHttpRequestFactory(client);
        final var requestFactory = new BufferingClientHttpRequestFactory(innerRequestFactory);

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

        final var inner = new RestTemplate(requestFactory);
        inner.setMessageConverters(converters);
        inner.setInterceptors(List.of(new RestInterceptors(upstreamId, interceptors)));
        inner.setErrorHandler(new UpstreamResponseErrorHandler(upstreamId, interceptors));
        this.upstreamId = upstreamId;
        this.interceptors = interceptors;
        this.rest = inner;
    }

    public <T> ResponseEntity<T> exchange(CONTEXT context, RequestEntity<?> requestEntity, Class<T> responseType) {
        return rest.exchange(makeEntity(requestEntity, context), responseType);
    }

    public <T> ResponseEntity<T> exchange(CONTEXT context, RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType) {
        return rest.exchange(makeEntity(requestEntity, context), responseType);
    }

    private RequestEntity<?> makeEntity(RequestEntity<?> requestEntity, CONTEXT context) {
        final var headers = new HttpHeaders();
        headers.addAll(requestEntity.getHeaders());
        for (var interceptor : interceptors) {
            final var newHeaders = interceptor.prepare(upstreamId, context, requestEntity);
            if (newHeaders != null) {
                headers.addAll(newHeaders);
            }
        }
        return new RequestEntity<>(requestEntity.getBody(), headers, requestEntity.getMethod(), requestEntity.getUrl(), requestEntity.getType());
    }

    public static class RestInterceptors implements ClientHttpRequestInterceptor {

        private final String upstreamId;
        private final List<UpstreamInterceptor> interceptors;

        public RestInterceptors(String upstreamId, List<UpstreamInterceptor> interceptors) {
            this.upstreamId = upstreamId;
            this.interceptors = interceptors;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] requestBodyBytes, ClientHttpRequestExecution execution) throws IOException {
            final HttpHeaders requestHeaders = request.getHeaders();
            final Resource requestBody = new ByteArrayResource(requestBodyBytes);
            final URI requestUri = request.getURI();
            for (var interceptor : interceptors) {
                interceptor.before(upstreamId, requestHeaders, requestUri, requestBody);
            }
            try {
                final ClientHttpResponse response = execution.execute(request, requestBodyBytes);
                final ByteArrayResource responseBody;
                try(final InputStream body = response.getBody()){
                    responseBody = new ByteArrayResource(StreamUtils.copyToByteArray(body));
                }
                for (var interceptor : interceptors) {
                    interceptor.after(upstreamId, requestHeaders, requestUri, requestBody, response.getStatusCode(), response.getHeaders(), responseBody);
                }
                return response;
            } catch (IOException | RuntimeException ex) {
                searchCauseOfType(ex, JsonMappingException.class).ifPresent(cex -> {
                    for (var interceptor : interceptors) {
                        interceptor.error(upstreamId, requestHeaders, requestUri, requestBody, cex);
                    }
                    throw new UpstreamException(upstreamId, "MAPPING_ERROR", cex.getMessage());
                });
                searchCauseOfType(ex, SocketException.class).ifPresent(cex -> {
                    for (var interceptor : interceptors) {
                        interceptor.error(upstreamId, requestHeaders, requestUri, requestBody, cex);
                    }
                    throw new UpstreamException(upstreamId, "UPSTREAM_DOWN", cex.getMessage());
                });
                for (var interceptor : interceptors) {
                    interceptor.error(upstreamId, requestHeaders, requestUri, requestBody, ex);
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

        public static HttpStatus statusCodeOrNull(ClientHttpResponse response) {
            try {
                return response.getStatusCode();
            } catch (IOException ex) {
                return null;
            }
        }

        private static final Set<String> LOGGED_MEDIA_TYPES = Set.of(
                "JSON",
                "TEXT",
                "XML",
                "HTML",
                "XHTML"
        );

        public static String bodyAsString(MediaType contentType, boolean logMultipart, InputStreamSource body) {
            if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_MIXED) && !logMultipart) {
                return "(multipart body)";
            }
            if (contentType != null && !LOGGED_MEDIA_TYPES.stream().anyMatch(t -> contentType.toString().toUpperCase().contains(t))) {
                return String.format("(binary:%s)", contentType);
            }
            try (var is = body.getInputStream()) {
                return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return String.format("(binary:%s)", contentType);
            }
        }

    }
}
