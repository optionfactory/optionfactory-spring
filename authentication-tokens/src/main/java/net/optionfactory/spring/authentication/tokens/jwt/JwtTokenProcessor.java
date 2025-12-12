package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import java.text.ParseException;
import java.util.List;
import net.optionfactory.spring.authentication.tokens.HeaderAndScheme;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.PrincipalAndAuthorities;
import net.optionfactory.spring.authentication.tokens.HttpHeaderAuthentication.TokenProcessor;
import org.springframework.security.authentication.BadCredentialsException;

public class JwtTokenProcessor implements TokenProcessor {

    final List<JwsProcessor> jwsProcessors;
    final List<JweProcessor> jweProcessors;

    public JwtTokenProcessor(List<JwsProcessor> jwsProcessors, List<JweProcessor> jweProcessors) {
        this.jwsProcessors = jwsProcessors;
        this.jweProcessors = jweProcessors;
    }

    @Override
    public PrincipalAndAuthorities process(HeaderAndScheme hs, String token) {
        final JWT jwt;
        try {
            jwt = JWTParser.parse(token);
        } catch (ParseException ex) {
            return null;
        }
        if (jwt instanceof SignedJWT jws) {
            final JWTClaimsSet claims;
            try {
                claims = jws.getJWTClaimsSet();
            } catch (ParseException ex) {
                //unparseable claims
                return null;
            }
            for (JwsProcessor proc : jwsProcessors) {
                if(!hs.equals(proc.hs())){
                    continue;
                }
                final var match = proc.matcher().matches(jws.getHeader(), claims, jws);
                if (match == Match.SKIP) {
                    continue;
                }
                try {
                    if (!jws.verify(proc.verifier())) {
                        throw new BadCredentialsException("invalid token signature");
                    }
                } catch (JOSEException | RuntimeException ex) {
                    if (match == Match.STRICT) {
                        throw new BadCredentialsException("invalid token", ex);
                    }
                    return null;
                }
                try {
                    proc.claimsVerifier().verify(claims, null);
                } catch (BadJWTException ex) {
                    throw new BadCredentialsException("invalid claims", ex);
                }
                final var principal = proc.principal().convert(jws.getHeader(), claims);
                if (principal == null) {
                    if (match == Match.STRICT) {
                        throw new BadCredentialsException("null principal");
                    }
                    return null;
                }
                final var authorities = proc.authorities().convert(jws.getHeader(), claims);
                return new PrincipalAndAuthorities(principal, authorities);
            }
            return null;
        }

        if (jwt instanceof EncryptedJWT jwe) {
            for (JweProcessor proc : jweProcessors) {
                if(!hs.equals(proc.hs())){
                    continue;
                }                
                final var match = proc.matcher().matches(jwe.getHeader(), jwe);
                if (match == Match.SKIP) {
                    continue;
                }
                try {
                    jwe.decrypt(proc.decrypter());
                } catch (JOSEException | RuntimeException ex) {
                    if (match == Match.STRICT) {
                        throw new BadCredentialsException("invalid jwe");
                    }                    
                    return null;
                }
                final JWTClaimsSet claims;
                try {
                    claims = jwe.getJWTClaimsSet();
                } catch (ParseException ex) {
                    throw new BadCredentialsException("unparseable claims", ex);
                }
                try {
                    proc.claimsVerifier().verify(claims, null);
                } catch (BadJWTException ex) {
                    throw new BadCredentialsException("invalid claims", ex);
                }
                final var principal = proc.principal().convert(jwe.getHeader(), claims);
                if (principal == null) {
                    if (match == Match.STRICT) {
                        throw new BadCredentialsException("null principal");
                    }
                    return null;
                }
                final var authorities = proc.authorities().convert(jwe.getHeader(), claims);
                return new PrincipalAndAuthorities(principal, authorities);
            }
            return null;
        }
        return null;

    }

    public record JwsProcessor(HeaderAndScheme hs, JwsMatcher matcher, JWSVerifier verifier, JWTClaimsSetVerifier claimsVerifier, JwtAuthoritiesConverter authorities, JwtPrincipalConverter principal) {

    }

    public record JweProcessor(HeaderAndScheme hs, JweMatcher matcher, JWEDecrypter decrypter, JWTClaimsSetVerifier claimsVerifier, JwtAuthoritiesConverter authorities, JwtPrincipalConverter principal) {

    }

}
