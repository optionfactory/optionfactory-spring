package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/// Decides how a JWS processor treats a signed token found on its header, before the token is
/// verified: see [Match] and [JwtTokenProcessor].
///
/// Everything it is given is unverified, attacker-supplied data: a matcher routes a token to the
/// processor that should verify it (by `kid`, by `iss`), and must never carry a security decision.
/// Routing on the claims is safe because the chosen processor still verifies the signature and
/// enforces its [ClaimsPolicy].
public interface JwsMatcher {

    /// @param header the token's header, unverified
    /// @param unverifiedClaims the token's claims, unverified
    /// @param jws the token
    /// @return how the processor treats the token
    Match matches(Header header, JWTClaimsSet unverifiedClaims, SignedJWT jws);
}
