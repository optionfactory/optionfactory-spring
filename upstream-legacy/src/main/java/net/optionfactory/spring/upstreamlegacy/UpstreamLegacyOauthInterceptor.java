package net.optionfactory.spring.upstreamlegacy;

import com.fasterxml.jackson.databind.JsonNode;
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
import net.optionfactory.spring.upstreamlegacy.UpstreamPort.Hints;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class UpstreamLegacyOauthInterceptor<T> implements UpstreamInterceptor<T> {

    private final Logger logger = LoggerFactory.getLogger(UpstreamLegacyOauthInterceptor.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<Future<KnownToken>> ft = new AtomicReference<>();
    private final RestTemplate restOauth;
    private final Supplier<Instant> clock;
    private final Function<PrepareContext<T>, RequestEntity<?>> requestFactory;

    public UpstreamLegacyOauthInterceptor(SSLConnectionSocketFactory socketFactory, Supplier<Instant> clock, Function<PrepareContext<T>, RequestEntity<?>> requestFactory) {
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
    public HttpHeaders prepare(Hints<T> hints, PrepareContext<T> prepare) {
        try {
            final String token = provideToken(prepare);
            final var headers = new HttpHeaders();
            headers.set("Authorization", String.format("Bearer %s", token));
            return headers;
        } catch (InterruptedException | ExecutionException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String provideToken(PrepareContext<T> prepare) throws InterruptedException, ExecutionException {
        final Instant now = clock.get();
        final var oldFuture = ft.get();
        final var oldToken = oldFuture == null ? null : oldFuture.get();
        logger.info("oldToken is {}", oldToken);
        if (oldToken == null || now.isAfter(oldToken.refetchAfter)) {
            logger.info("refetching", oldToken);
            ft.set(executor.submit(() -> fetchToken(prepare, requestFactory.apply(prepare), now)));
        }
        if (oldToken != null && !now.isAfter(oldToken.discardAfter)) {
            logger.info("old token is valid", oldToken);
            return oldToken.token;
        }
        final KnownToken fresh = ft.get().get();
        logger.info("fresh token", fresh);
        return fresh.token;
    }

    protected KnownToken fetchToken(PrepareContext<T> prepare, RequestEntity<?> entity, final Instant now) throws RestClientException {
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

    public static UpstreamLegacyOauthInterceptor clientCredentials(
            String clientId,
            String clientSecret,
            SSLConnectionSocketFactory socketFactory,
            URI tokenUri,
            Supplier<Instant> clock) {
        final var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON_UTF8));
        headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"));
        headers.setBasicAuth(clientId, clientSecret);
        final var formParameters = new LinkedMultiValueMap<String, String>();
        formParameters.add("grant_type", "client_credentials");
        final var re = new RequestEntity<>(formParameters, headers, HttpMethod.POST, tokenUri);
        return new UpstreamLegacyOauthInterceptor(socketFactory, clock, prepareCtx -> re);
    }

    public static UpstreamLegacyOauthInterceptor password(
            String clientId,
            String clientSecret,
            SSLConnectionSocketFactory socketFactory,
            URI tokenUri,
            Supplier<Instant> clock) {
        final HttpHeaders oHeaders = new HttpHeaders();
        oHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        final LinkedMultiValueMap<String, String> oBody = new LinkedMultiValueMap<>();
        oBody.add("username", "admin");
        oBody.add("password", clientSecret);
        oBody.add("grant_type", "password");
        oBody.add("client_id", clientId);
        final var re = new RequestEntity<>(oBody, oHeaders, HttpMethod.POST, tokenUri);
        return new UpstreamLegacyOauthInterceptor(socketFactory, clock, prepareCtx -> re);
    }

    public static class KnownToken {

        public final String token;
        public final Instant discardAfter;
        public final Instant refetchAfter;

        public KnownToken(String token, Instant refetchAfter, Instant discardAfter) {
            this.token = token;
            this.refetchAfter = refetchAfter;
            this.discardAfter = discardAfter;
        }

        @Override
        public String toString() {
            return String.format("t:<removed>, d:%s, r:%s", discardAfter, refetchAfter);
        }

    }
}
