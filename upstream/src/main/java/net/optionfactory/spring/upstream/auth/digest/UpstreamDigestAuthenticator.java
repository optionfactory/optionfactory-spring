package net.optionfactory.spring.upstream.auth.digest;

import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.auth.RestClientAuthenticationException;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class UpstreamDigestAuthenticator implements UpstreamHttpRequestInitializer {

    private final DigestAuth digestAuth;
    private RestClient restClient;

    public UpstreamDigestAuthenticator(String clientId, String clientSecret) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
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
            final var challenge = restClient.method(HttpMethod.POST)
                    .uri(request.getURI())
                    .exchange((ereq, eres) -> eres.getHeaders().getFirst("WWW-Authenticate"));
            request.getHeaders().set("Authorization", digestAuth.authHeader(request.getMethod().name(), request.getURI().getPath(), challenge));
        } catch (RuntimeException ex) {
            throw new RestClientAuthenticationException(invocation.upstream(), invocation.endpoint(), ex);
        }
    }

}
