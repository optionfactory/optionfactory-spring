package net.optionfactory.spring.authentication.tokens;

import java.util.List;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.AuthenticatedToken;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.TokenProcessor;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.UnauthenticatedToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Authenticates by matching the configured static token with the request Bearer
 * token, granting the configured authorities on authentication success.
 */
public class HttpHeaderAuthenticationProvider implements AuthenticationProvider {

    private final List<TokenProcessor> processors;

    public HttpHeaderAuthenticationProvider(List<TokenProcessor> processors) {
        this.processors = processors;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final var token = (UnauthenticatedToken) authentication;
        for (final var processor : processors) {
            final var paa = processor.process(token.getCredentials());
            if (paa != null) {
                return new AuthenticatedToken(token.getCredentials(), paa.principal(), token.getDetails(), paa.authorities());
            }
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UnauthenticatedToken.class.isAssignableFrom(authentication);
    }

}
