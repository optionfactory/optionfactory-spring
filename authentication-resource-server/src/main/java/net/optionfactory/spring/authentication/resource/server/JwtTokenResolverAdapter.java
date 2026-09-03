package net.optionfactory.spring.authentication.resource.server;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/// Lets spring's oauth2 resource server share the `Authorization` header with another
/// bearer-token mechanism, by declining the tokens that are not its own.
///
/// It is needed because `BearerTokenAuthenticationFilter` claims every bearer token its resolver
/// returns: it hands the token to the configured `JwtDecoder` and fails the request when the decoder
/// cannot verify it, without checking whether something else has already authenticated the caller.
/// So in an application where the resource server sits alongside, say, `authentication-tokens`, a
/// static integration token or a locally signed jws reaches the resource server after being
/// authenticated and is rejected there. Giving the resource server a resolver that returns `null`
/// for those tokens is what keeps the two mechanisms from contending.
///
/// The predicate decides from the jws header alone, before any signature is checked -- typically on
/// `kid` (an authorization server that publishes a jwks names its keys; a token an application signs
/// for itself usually does not) or on the algorithm. Two consequences follow. It must be enough to
/// tell the mechanisms apart on its own, and it must never carry a security decision: the header is
/// unverified attacker-supplied data at that point, and all this chooses is which mechanism gets to
/// verify it.
///
/// ```java
/// http.oauth2ResourceServer(rs -> rs
///         .bearerTokenResolver(new JwtTokenResolverAdapter(header -> header instanceof JWSHeader jws && jws.getKeyID() != null))
///         .jwt(jwt -> jwt.decoder(decoder)));
/// ```
public class JwtTokenResolverAdapter implements BearerTokenResolver {

    private final String headerName = "Authorization";
    private final String authScheme = "BEARER ";
    private final Predicate<Header> predicate;

    /// Resolves bearer tokens the predicate accepts, and only those.
    ///
    /// @param predicate true when this resource server should handle the token, false to leave it
    /// to another mechanism
    public JwtTokenResolverAdapter(Predicate<Header> predicate) {
        this.predicate = predicate;
    }

    /// @return the token when the header carries a bearer jwt the predicate accepts, `null`
    /// otherwise -- including for an unparseable token, which is left to whatever else may claim it
    @Override
    public String resolve(HttpServletRequest request) {
        return searchToken(request, headerName, authScheme)
                .flatMap(token -> {
                    try{
                        final JWT jwt = JWTParser.parse(token);
                        final var accepted = predicate.test(jwt.getHeader());
                        return accepted ? Optional.of(token) : Optional.empty();
                    }catch(ParseException ex){
                        return Optional.empty();
                    }
                })
                .orElse(null);
    }

    /// Reads a token out of an auth-scheme header, case-insensitively on the scheme. Exposed
    /// because a resolver for a different mechanism needs the same reading.
    ///
    /// @param request the request to read
    /// @param headerName the header carrying the credential
    /// @param authScheme the scheme prefix, including its trailing space
    /// @return the token, or empty when the header is absent or carries another scheme
    public static Optional<String> searchToken(HttpServletRequest request, String headerName, String authScheme) {
        return Optional.ofNullable(request.getHeader(headerName))
                .filter(value -> value.toUpperCase(Locale.ROOT).startsWith(authScheme))
                .map(value -> value.substring(authScheme.length()).trim());
    }
}
