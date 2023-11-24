package net.optionfactory.spring.upstream.auth.digest;

import java.io.IOException;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.auth.RestClientAuthenticationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

public class UpstreamDigestAuthenticationInterceptor implements UpstreamHttpInterceptor {

    private final DigestAuth digestAuth;
    private RestClient restClient;

    public UpstreamDigestAuthenticationInterceptor(String clientId, String clientSecret) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
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
            final var challenge = restClient.method(HttpMethod.POST)
                    .uri(request.getURI())
                    .exchange((ereq, eres) -> eres.getHeaders().getFirst("WWW-Authenticate"));
            request.getHeaders().set("Authorization", digestAuth.authHeader(request.getMethod().name(), request.getURI().getPath(), challenge));
        } catch (RuntimeException ex) {
            throw new RestClientAuthenticationException(String.format("Authentication failed for %s:%s", ctx.upstream(), ctx.endpoint()), ex);
        }
        return execution.execute(request, body);
    }

}
