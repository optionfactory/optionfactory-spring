package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.EncryptedJWT;

/// Decides how a JWE processor treats an encrypted token found on its header, before the token is
/// decrypted: see [Match] and [JwtTokenProcessor].
///
/// Only the header, unverified and in clear, is available at that point (typically to route by
/// `kid` or by algorithm): the claims are still encrypted.
public interface JweMatcher {

    /// @param header the token's header, unverified
    /// @param jwe the token, still encrypted
    /// @return how the processor treats the token
    Match matches(Header header, EncryptedJWT jwe);
}
