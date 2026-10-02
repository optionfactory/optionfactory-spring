package net.optionfactory.spring.authentication.tokens;

import java.util.List;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.AuthenticatedToken;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.TokenProcessor;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.UnauthenticatedToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/// Authenticates an [UnauthenticatedToken] by running the configured [TokenProcessor]s in order,
/// until one of them accepts it.
///
/// The first processor accepting the token decides its principal and authorities, a processor
/// rejecting it with an `AuthenticationException` ends the search, and a token no processor accepts
/// yields `null`: in spring's `ProviderManager` terms this provider did not authenticate it, and the
/// manager fails with a `ProviderNotFoundException` when no other provider does.
public class HttpHeaderAuthenticationProvider implements AuthenticationProvider {

    private final List<TokenProcessor> processors;

    /// @param processors the processors, run in order
    public HttpHeaderAuthenticationProvider(List<TokenProcessor> processors) {
        this.processors = processors;
    }

    /// @param authentication an [UnauthenticatedToken]
    /// @return an [AuthenticatedToken] carrying the token, the request details and what the
    /// accepting processor granted, or `null` when no processor accepts the token
    /// @throws AuthenticationException when a processor rejects the token
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final var token = (UnauthenticatedToken) authentication;
        for (final var processor : processors) {
            final var paa = processor.process(token.getHeaderAndScheme(), token.getCredentials());
            if (paa != null) {
                return new AuthenticatedToken(token.getCredentials(), paa.principal(), token.getDetails(), paa.authorities());
            }
        }
        return null;
    }

    /// @return true for [UnauthenticatedToken]s only
    @Override
    public boolean supports(Class<?> authentication) {
        return UnauthenticatedToken.class.isAssignableFrom(authentication);
    }

}
