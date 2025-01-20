package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public interface JwtAuthoritiesConverter {

    Collection<? extends GrantedAuthority> convert(Header header, JWTClaimsSet claims);

}
