package net.optionfactory.spring.upstream.auth.digest;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.auth.RestClientAuthenticationException;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.web.client.RestClient;

public class UpstreamDigestAuthenticator implements UpstreamHttpRequestInitializer {

    private final DigestAuth digestAuth;
    private final RestClient restClient;

    public UpstreamDigestAuthenticator(String clientId, String clientSecret, RestClient restClient) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
        this.restClient = restClient;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        try {
            final var challenge = restClient.method(HttpMethod.POST)
                    .uri(request.getURI())
                    .exchange((ereq, eres) -> eres.getHeaders().getFirst("WWW-Authenticate"));
            request.getHeaders().set("Authorization", digestAuth.authHeader(request.getMethod().name(), request.getURI().getPath(), challenge));
        } catch (RuntimeException ex) {
            throw new RestClientAuthenticationException(invocation.endpoint().upstream(), invocation.endpoint().name(), ex);
        }
    }

}
