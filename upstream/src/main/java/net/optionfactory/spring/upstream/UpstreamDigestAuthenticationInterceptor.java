package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamPort.Hints;
import net.optionfactory.spring.upstream.digest.DigestAuth;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public class UpstreamDigestAuthenticationInterceptor<T> implements UpstreamInterceptor<T> {

    private final DigestAuth digestAuth;
    private final CloseableHttpClient authClient;

    public UpstreamDigestAuthenticationInterceptor(String clientId, String clientSecret, CloseableHttpClient authClient) {
        this.digestAuth = DigestAuth.fromCredentials(clientId, clientSecret);
        this.authClient = authClient;
    }

    @Override
    public HttpHeaders prepare(Hints<T> hints, PrepareContext<T> prepare) {
        final HttpMethod method = prepare.entity.getMethod();
        final URI uri = prepare.entity.getUrl();
        final String uriPath = uri.getPath();
        final String serverChallenge = challenge(prepare, uri);        
        final HttpHeaders h = new HttpHeaders();
        h.set("Authorization", digestAuth.authHeader(method.name(), uriPath, serverChallenge));
        return h;
    }

    private String challenge(PrepareContext<T> prepare, URI uri) {
        try (CloseableHttpResponse response = authClient.execute(new HttpPost(uri))) {
            return response.getFirstHeader("WWW-Authenticate").getValue();
        } catch (IOException ex) {
            throw new UpstreamException(prepare.upstreamId, "Authentication", ex.getMessage());
        }
    }

}
