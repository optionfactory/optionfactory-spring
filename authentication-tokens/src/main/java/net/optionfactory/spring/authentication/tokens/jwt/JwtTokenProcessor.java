package net.optionfactory.spring.authentication.tokens.jwt;

import com.nimbusds.jose.Header;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.proc.SecurityContext;
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

/// Authenticates a JWT with the first processor configured for its header whose matcher does not
/// skip it.
///
/// A signed JWT is accepted once its signature verifies and its claims satisfy the processor's
/// [ClaimsPolicy]. An encrypted JWT is decrypted first, and then trusted in one of two ways. With an
/// inner verifier, its payload must be a nested signed JWT (`JWE(JWS(claims))`) whose signature
/// verifies: this is mandatory for asymmetric JWE, since decrypting only proves the token was encrypted
/// to us, not who authored it, and anyone holding the public key can encrypt. Without one — symmetric
/// JWE only — the payload is read as raw claims, the shared secret being the trust root. See
/// [JweAuthenticationConfigurer].
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
                final Header header;
                if (proc.innerVerifier() != null) {
                    final SignedJWT inner;
                    try {
                        inner = SignedJWT.parse(jwe.getPayload().toString());
                    } catch (ParseException ex) {
                        if (match == Match.STRICT) {
                            throw new BadCredentialsException("inner payload is not a signed jwt", ex);
                        }
                        return null;
                    }
                    try {
                        if (!inner.verify(proc.innerVerifier())) {
                            throw new BadCredentialsException("invalid inner token signature");
                        }
                    } catch (JOSEException | RuntimeException ex) {
                        if (match == Match.STRICT) {
                            throw new BadCredentialsException("invalid inner token", ex);
                        }
                        return null;
                    }
                    try {
                        claims = inner.getJWTClaimsSet();
                    } catch (ParseException ex) {
                        throw new BadCredentialsException("unparseable claims", ex);
                    }
                    header = inner.getHeader();
                } else {
                    try {
                        claims = jwe.getJWTClaimsSet();
                    } catch (ParseException ex) {
                        throw new BadCredentialsException("unparseable claims", ex);
                    }
                    header = jwe.getHeader();
                }
                try {
                    proc.claimsVerifier().verify(claims, null);
                } catch (BadJWTException ex) {
                    throw new BadCredentialsException("invalid claims", ex);
                }
                final var principal = proc.principal().convert(header, claims);
                if (principal == null) {
                    if (match == Match.STRICT) {
                        throw new BadCredentialsException("null principal");
                    }
                    return null;
                }
                final var authorities = proc.authorities().convert(header, claims);
                return new PrincipalAndAuthorities(principal, authorities);
            }
            return null;
        }
        return null;

    }

    public record JwsProcessor(HeaderAndScheme hs, JwsMatcher matcher, JWSVerifier verifier, JWTClaimsSetVerifier<SecurityContext> claimsVerifier, JwtAuthoritiesConverter authorities, JwtPrincipalConverter principal) {

    }

    public record JweProcessor(HeaderAndScheme hs, JweMatcher matcher, JWEDecrypter decrypter, JWSVerifier innerVerifier, JWTClaimsSetVerifier<SecurityContext> claimsVerifier, JwtAuthoritiesConverter authorities, JwtPrincipalConverter principal) {

    }

}
