package net.optionfactory.spring.upstream.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

public class UpstreamOAuthClientCredentialsInterceptor implements UpstreamHttpInterceptor {

    private final String basicHeader;
    private final URI tokenUri;
    private RestClient restClient;

    public UpstreamOAuthClientCredentialsInterceptor(String clientId, String clientSecret, URI tokenUri) {
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
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
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
                    .body(JsonNode.class)
                    .get("access_token")
                    .asText();

            request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", token));
        } catch (RuntimeException ex) {
            throw new RestClientAuthenticationException("Authentication failed", ex);
        }

        return execution.execute(request, body);
    }
}
