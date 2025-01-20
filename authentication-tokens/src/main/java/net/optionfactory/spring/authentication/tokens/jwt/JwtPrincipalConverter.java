package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;

public interface JwtPrincipalConverter {

    Object convert(Header header, JWTClaimsSet claims);

}
