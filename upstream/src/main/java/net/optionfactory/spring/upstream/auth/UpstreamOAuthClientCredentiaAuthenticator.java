package net.optionfactory.spring.upstream.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

public class UpstreamOAuthClientCredentiaAuthenticator implements UpstreamHttpRequestInitializer {

    private final String basicHeader;
    private final URI tokenUri;
    private RestClient restClient;

    public UpstreamOAuthClientCredentiaAuthenticator(String clientId, String clientSecret, URI tokenUri) {
        this.basicHeader = String.format("Basic %s", HttpHeaders.encodeBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8));
        this.tokenUri = tokenUri;
    }

    @Override
    public void preprocess(Class<?> k, ClientHttpRequestFactory rf) {
        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .build();
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        try {
            final var oBody = new LinkedMultiValueMap<String, String>();
            oBody.add("grant_type", "client_credentials");

            final var token = restClient.post()
                    .uri(tokenUri)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"))
                    .header(HttpHeaders.AUTHORIZATION, basicHeader)
                    .body(oBody)
                    .retrieve()
                    .body(JsonNode.class);
            final var accesstoken = token.get("access_token").asText();

            request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accesstoken));
        } catch (RuntimeException ex) {
            throw new RestClientAuthenticationException(invocation.upstream(), invocation.endpoint(), ex);
        }
    }
}
