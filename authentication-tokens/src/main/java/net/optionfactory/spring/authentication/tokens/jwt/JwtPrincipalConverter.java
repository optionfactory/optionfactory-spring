package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;

/// Derives the principal of an accepted JWT, typically from its `sub`.
public interface JwtPrincipalConverter {

    /// Called only once the token has been verified and its claims satisfy the [ClaimsPolicy].
    ///
    /// @param header the header of the token that was verified: for a nested JWE, the inner JWS's
    /// @param claims the verified claims
    /// @return the principal; `null` rejects the token
    Object convert(Header header, JWTClaimsSet claims);

}
