package net.optionfactory.spring.upstream;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class UpstreamOAuthPasswordInterceptor<T> implements UpstreamInterceptor<T> {

    private final Logger logger = Logger.getLogger(UpstreamOAuthPasswordInterceptor.class);
    private final String clientId;
    private final String clientSecret;
    private final URI tokenURI;
    private final RestTemplate restOauth;

    public UpstreamOAuthPasswordInterceptor(String clientId, String clientSecret, SSLConnectionSocketFactory socketFactory, URI tokenURI) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenURI = tokenURI;

        final var builder = HttpClientBuilder.create();
        builder.setSSLSocketFactory(socketFactory);
        final var client = builder.setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(5000).build())
                .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
                .build();
        final var requestFactory = new HttpComponentsClientHttpRequestFactory(client);
        this.restOauth = new RestTemplate(requestFactory);
    }

    @Override
    public HttpHeaders prepare(String upstreamId, T ctx, RequestEntity<?> entity) {
        final String token = getOauthToken();

        final var headers = new HttpHeaders();
        headers.set("Authorization", String.format("Bearer %s", token));
        return headers;
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
