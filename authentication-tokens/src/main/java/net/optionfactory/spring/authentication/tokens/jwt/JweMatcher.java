package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.EncryptedJWT;

public interface JweMatcher {

    Match matches(Header header, EncryptedJWT jwe);
}
