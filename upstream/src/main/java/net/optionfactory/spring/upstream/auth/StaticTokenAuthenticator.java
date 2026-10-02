package net.optionfactory.spring.upstream.auth;

import java.nio.charset.Charset;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates requests with a fixed credential, set as `<scheme> <token>` in a header (replacing
/// any value the header already had).
///
/// ```java
/// UpstreamBuilder.create(PaymentsClient.class)
///         .initializer(StaticTokenAuthenticator.bearer(apiKey))
///         ...
/// ```
public class StaticTokenAuthenticator implements UpstreamHttpRequestInitializer {

    private final String header;
    private final String scheme;
    private final String token;

    /// @param header the header carrying the credential
    /// @param scheme the authentication scheme, prepended to the token with a space
    /// @param token the credential
    public StaticTokenAuthenticator(String header, String scheme, String token) {
        this.header = header;
        this.scheme = scheme;
        this.token = token;
    }

    /// @param invocation the invocation, unused
    /// @param request the request to authenticate
    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        request.getHeaders().set(header, String.format("%s %s", scheme, token));
    }

    /// @param scheme the authentication scheme
    /// @param token the credential
    /// @return an authenticator setting `Authorization: <scheme> <token>`
    public static StaticTokenAuthenticator authorization(String scheme, String token) {
        return new StaticTokenAuthenticator("Authorization", scheme, token);
    }

    /// @param token the bearer token
    /// @return an authenticator setting `Authorization: Bearer <token>`
    public static StaticTokenAuthenticator bearer(String token) {
        return new StaticTokenAuthenticator("Authorization", "Bearer", token);
    }

    /// @param username the username
    /// @param password the password
    /// @param charset the charset the credentials are encoded with before base64
    /// @return an authenticator setting `Authorization: Basic <base64(username:password)>`
    /// @throws IllegalArgumentException when the credentials cannot be encoded in the charset
    public static StaticTokenAuthenticator basic(String username, String password, Charset charset) {
        final var credentials = HttpHeaders.encodeBasicAuth(username, password, charset);
        return new StaticTokenAuthenticator("Authorization", "Basic", credentials);
    }

    /// Authenticates against a proxy, whatever the upstream authentication is.
    ///
    /// @param username the username
    /// @param password the password
    /// @param charset the charset the credentials are encoded with before base64
    /// @return an authenticator setting `Proxy-Authorization: Basic <base64(username:password)>`
    /// @throws IllegalArgumentException when the credentials cannot be encoded in the charset
    public static StaticTokenAuthenticator proxyBasic(String username, String password, Charset charset) {
        final var credentials = HttpHeaders.encodeBasicAuth(username, password, charset);
        return new StaticTokenAuthenticator("Proxy-Authorization", "Basic", credentials);
    }

}
