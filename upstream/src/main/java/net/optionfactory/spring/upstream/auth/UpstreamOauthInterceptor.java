package net.optionfactory.spring.upstream.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class UpstreamOauthInterceptor<T> implements UpstreamHttpInterceptor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicReference<Future<KnownToken>> ft = new AtomicReference<>();
    private final RestTemplate restOauth;
    private final Supplier<Instant> clock;
    private final Function<InvocationContext, RequestEntity<?>> requestFactory;

    public UpstreamOauthInterceptor(SSLConnectionSocketFactory socketFactory, Supplier<Instant> clock, Function<InvocationContext, RequestEntity<?>> requestFactory) {
        final var client = HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(socketFactory)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(5, TimeUnit.SECONDS).build())
                        .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                        .build())
                .build();
        this.restOauth = new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
        this.clock = clock;
        this.requestFactory = requestFactory;
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            request.getHeaders().set("Authorization", String.format("Bearer %s", provideToken(ctx)));
        } catch (InterruptedException | ExecutionException ex) {
            throw new IllegalStateException(ex);
        }
        return execution.execute(request, body);
    }

    private String provideToken(InvocationContext ctx) throws InterruptedException, ExecutionException {
        final Instant now = clock.get();
        final var oldFuture = ft.get();
        final var oldToken = oldFuture == null ? null : oldFuture.get();
        if (oldToken == null || now.isAfter(oldToken.refetchAfter)) {
            ft.set(executor.submit(() -> fetchToken(ctx, requestFactory.apply(ctx), now)));
        }
        if (oldToken != null && !now.isAfter(oldToken.discardAfter)) {
            return oldToken.token;
        }
        final KnownToken fresh = ft.get().get();
        return fresh.token;
    }

    protected KnownToken fetchToken(InvocationContext ctx, RequestEntity<?> entity, final Instant now) throws RestClientException {
        final ResponseEntity<JsonNode> oResponse = restOauth.exchange(entity, JsonNode.class);
        final var body = oResponse.getBody();
        final var expiresIn = body.get("expires_in").asInt(0);
        final var token = body.get("access_token").asText();
        return new KnownToken(
                token,
                now.plusSeconds(expiresIn).minusSeconds(Math.max(15, expiresIn / 2)),
                now.plusSeconds(expiresIn).minusSeconds(Math.max(5, expiresIn / 4))
        );
    }

    public static UpstreamOauthInterceptor clientCredentials(
            String clientId,
            String clientSecret,
            SSLConnectionSocketFactory socketFactory,
            URI tokenUri,
            Supplier<Instant> clock) {
        final var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"));
        headers.setBasicAuth(clientId, clientSecret);
        final var formParameters = new LinkedMultiValueMap<String, String>();
        formParameters.add("grant_type", "client_credentials");
        final var re = new RequestEntity<>(formParameters, headers, HttpMethod.POST, tokenUri);
        return new UpstreamOauthInterceptor(socketFactory, clock, ctx -> re);
    }

    public static UpstreamOauthInterceptor password(
            String clientId,
            String clientSecret,
            SSLConnectionSocketFactory socketFactory,
            URI tokenUri,
            Supplier<Instant> clock) {
        final HttpHeaders oHeaders = new HttpHeaders();
        oHeaders.setContentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"));
        final LinkedMultiValueMap<String, String> oBody = new LinkedMultiValueMap<>();
        oBody.add("username", "admin");
        oBody.add("password", clientSecret);
        oBody.add("grant_type", "password");
        oBody.add("client_id", clientId);
        final var re = new RequestEntity<>(oBody, oHeaders, HttpMethod.POST, tokenUri);
        return new UpstreamOauthInterceptor(socketFactory, clock, ctx -> re);
    }

    public record KnownToken(String token, Instant refetchAfter, Instant discardAfter) {

    }

}
