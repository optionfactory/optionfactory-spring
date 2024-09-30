package net.optionfactory.spring.upstream.auth;

import java.nio.charset.Charset;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

public class StaticTokenAuthenticator implements UpstreamHttpRequestInitializer {

    private final String header;
    private final String scheme;
    private final String token;

    public StaticTokenAuthenticator(String header, String scheme, String token) {
        this.header = header;
        this.scheme = scheme;
        this.token = token;
    }

    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        request.getHeaders().add(header, String.format("%s %s", scheme, token));
    }

    public static StaticTokenAuthenticator authorization(String scheme, String token) {
        return new StaticTokenAuthenticator("Authorization", scheme, token);
    }

    public static StaticTokenAuthenticator bearer(String token) {
        return new StaticTokenAuthenticator("Authorization", "Bearer", token);
    }

    public static StaticTokenAuthenticator basic(String username, String password, Charset charset) {
        final var credentials = HttpHeaders.encodeBasicAuth(username, password, charset);
        return new StaticTokenAuthenticator("Authorization", "Basic", credentials);
    }

    public static StaticTokenAuthenticator proxyBasic(String username, String password, Charset charset) {
        final var credentials = HttpHeaders.encodeBasicAuth(username, password, charset);
        return new StaticTokenAuthenticator("Proxy-Authorization", "Basic", credentials);
    }

}
