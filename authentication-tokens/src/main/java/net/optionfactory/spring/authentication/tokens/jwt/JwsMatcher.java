package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public interface JwsMatcher {

    Match matches(Header header, JWTClaimsSet unverifiedClaims, SignedJWT jws);
}
