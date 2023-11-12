package net.optionfactory.spring.upstream.auth.digest;

import java.io.IOException;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

public class UpstreamDigestAuthenticationInterceptor implements UpstreamHttpInterceptor {

    private final DigestAuth digestAuth;
    private final CloseableHttpClient authClient;

    public UpstreamDigestAuthenticationInterceptor(String clientId, String clientSecret, CloseableHttpClient authClient) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
        this.authClient = authClient;
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        try {
            final String challenge = authClient.execute(
                    new HttpPost(request.getURI()),
                    response -> response.getFirstHeader("WWW-Authenticate").getValue()
            );
            request.getHeaders().set("Authorization", digestAuth.authHeader(request.getMethod().name(), request.getURI().getPath(), challenge));
        } catch (IOException | RuntimeException ex) {
            throw new RestClientException("Authentication failed", ex);
        }
        return execution.execute(request, body);
    }

}
