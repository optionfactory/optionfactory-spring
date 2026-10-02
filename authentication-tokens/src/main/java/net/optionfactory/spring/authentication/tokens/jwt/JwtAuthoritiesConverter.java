package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/// Derives the authorities of an accepted JWT. The default is [RolesGroupsAndScopesFromClaims].
public interface JwtAuthoritiesConverter {

    /// Called only once the token has been verified and its claims satisfy the [ClaimsPolicy].
    ///
    /// @param header the header of the token that was verified: for a nested JWE, the inner JWS's
    /// @param claims the verified claims
    /// @return the authorities granted to the request
    /// @throws org.springframework.security.authentication.BadCredentialsException to reject the
    /// token, e.g. for claims that cannot be read
    Collection<? extends GrantedAuthority> convert(Header header, JWTClaimsSet claims);

}
