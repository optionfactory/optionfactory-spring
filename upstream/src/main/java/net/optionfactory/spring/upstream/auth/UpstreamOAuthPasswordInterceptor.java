package net.optionfactory.spring.upstream.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

public class UpstreamOAuthPasswordInterceptor implements UpstreamHttpInterceptor {

    private final String clientId;
    private final String clientSecret;
    private final URI tokenUri;
    private RestClient restClient;

    public UpstreamOAuthPasswordInterceptor(String clientId, String clientSecret, URI tokenUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
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
            oBody.add("username", "admin");
            oBody.add("password", clientSecret);
            oBody.add("grant_type", "password");
            oBody.add("client_id", clientId);

            final var token = restClient.post()
                    .uri(tokenUri)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"))
                    .body(oBody)
                    .retrieve()
                    .body(JsonNode.class)
                    .get("access_token")
                    .asText();

            request.getHeaders().set("Authorization", String.format("Bearer %s", token));
        } catch (RuntimeException ex) {
            throw new RestClientAuthenticationException("Authentication failed", ex);
        }
        return execution.execute(request, body);
    }

}
