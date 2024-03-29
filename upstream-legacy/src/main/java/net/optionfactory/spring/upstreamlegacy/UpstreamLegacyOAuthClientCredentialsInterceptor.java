package net.optionfactory.spring.upstreamlegacy;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.optionfactory.spring.upstreamlegacy.UpstreamPort.Hints;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class UpstreamLegacyOAuthClientCredentialsInterceptor<T> implements UpstreamInterceptor<T> {

    private final String clientId;
    private final String clientSecret;
    private final URI tokenUri;
    private final RestTemplate restOauth;

    public UpstreamLegacyOAuthClientCredentialsInterceptor(String clientId, String clientSecret, SSLConnectionSocketFactory socketFactory, URI tokenUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        final var client = HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(socketFactory)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(5, TimeUnit.SECONDS).build())
                        .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                        .build())
                .build();
        final var requestFactory = new HttpComponentsClientHttpRequestFactory(client);

        this.restOauth = new RestTemplate(requestFactory);

    }

    @Override
    public HttpHeaders prepare(Hints<T> hints, PrepareContext<T> prepare) {
        final String token = getOauthToken();
        final var headers = new HttpHeaders();
        headers.set("Authorization", String.format("Bearer %s", token));
        return headers;
    }

    protected String getOauthToken() throws RestClientException {
        final var headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON_UTF8));
        headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"));
        headers.setBasicAuth(clientId, clientSecret);
        final var formParameters = new LinkedMultiValueMap<String, String>();
        formParameters.add("grant_type", "client_credentials");
        final RequestEntity<LinkedMultiValueMap<String, String>> re = new RequestEntity<>(formParameters, headers, HttpMethod.POST, tokenUri);
        final ResponseEntity<JsonNode> oResponse = restOauth.exchange(re, JsonNode.class);
        return oResponse.getBody().get("access_token").asText();
    }

}
