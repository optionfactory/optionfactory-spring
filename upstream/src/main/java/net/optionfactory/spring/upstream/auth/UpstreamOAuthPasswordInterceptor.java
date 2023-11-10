package net.optionfactory.spring.upstream.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.SocketConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class UpstreamOAuthPasswordInterceptor implements UpstreamHttpInterceptor {

    private final String clientId;
    private final String clientSecret;
    private final URI tokenURI;
    private final RestTemplate restOauth;

    public UpstreamOAuthPasswordInterceptor(String clientId, String clientSecret, SSLConnectionSocketFactory socketFactory, URI tokenURI) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenURI = tokenURI;

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
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        final String token = getOauthToken();
        request.getHeaders().set("Authorization", String.format("Bearer %s", token));
        return execution.execute(request, body);
    }

    private String getOauthToken() throws RestClientException {
        final HttpHeaders oHeaders = new HttpHeaders();
        oHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        final LinkedMultiValueMap<String, String> oBody = new LinkedMultiValueMap<>();
        oBody.add("username", "admin");
        oBody.add("password", clientSecret);
        oBody.add("grant_type", "password");
        oBody.add("client_id", clientId);
        HttpEntity<LinkedMultiValueMap<String, String>> oEntity = new HttpEntity<>(oBody, oHeaders);
        final ResponseEntity<JsonNode> oResponse = restOauth.postForEntity(tokenURI, oEntity, JsonNode.class);
        return oResponse.getBody().get("access_token").asText();
    }

}
