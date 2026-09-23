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

/// Authenticates a JWT with the processors configured for its header, in configuration order.
///
/// Each processor's matcher decides how it treats a token, before anything is verified:
/// [Match#SKIP] leaves it to the next processor, [Match#STRICT] claims it, so that if this processor
/// rejects it the token is rejected, and [Match#LAX] tries it and, if this processor rejects it,
/// passes it on to the next one. A processor rejects a token when its signature (or decryption) fails,
/// its claims do not satisfy the [ClaimsPolicy], or no principal is derived from it. Several issuers
/// can therefore share a header either with `LAX` processors, each trying the token in turn, or with
/// matchers routing each token by its unverified header or claims (a `kid`, an `iss`) to the one
/// processor that `STRICT`ly owns it, which also keeps a token from being checked against every key.
///
/// A signed JWT is accepted once its signature verifies and its claims satisfy the processor's
/// [ClaimsPolicy]. An encrypted JWT is decrypted first, and then trusted in one of two ways. With an
/// inner verifier, its payload must be a nested signed JWT (`JWE(JWS(claims))`) whose signature
/// verifies: this is mandatory for asymmetric JWE, since decrypting only proves the token was encrypted
/// to us, not who authored it, and anyone holding the public key can encrypt. Without one — symmetric
/// JWE only — the payload is read as raw claims, the shared secret being the trust root. See
/// [JweAuthenticationConfigurer]. Each processor decrypts its own copy of the token, so one that fails
/// halfway leaves nothing behind for the next.
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
                if (!hs.equals(proc.hs())) {
                    continue;
                }
                final var match = proc.matcher().matches(jws.getHeader(), claims, jws);
                if (match == Match.SKIP) {
                    continue;
                }
                try {
                    return authenticate(proc, jws, claims);
                } catch (BadCredentialsException ex) {
                    if (match == Match.STRICT) {
                        throw ex;
                    }
                }
            }
            return null;
        }
        if (jwt instanceof EncryptedJWT jwe) {
            for (JweProcessor proc : jweProcessors) {
                if (!hs.equals(proc.hs())) {
                    continue;
                }
                final var match = proc.matcher().matches(jwe.getHeader(), jwe);
                if (match == Match.SKIP) {
                    continue;
                }
                try {
                    return authenticate(proc, token);
                } catch (BadCredentialsException ex) {
                    if (match == Match.STRICT) {
                        throw ex;
                    }
                }
            }
            return null;
        }
        return null;
    }

    private static PrincipalAndAuthorities authenticate(JwsProcessor proc, SignedJWT jws, JWTClaimsSet claims) {
        verify(jws, proc.verifier(), "invalid token");
        return accept(proc.claimsVerifier(), proc.principal(), proc.authorities(), jws.getHeader(), claims);
    }

    private static PrincipalAndAuthorities authenticate(JweProcessor proc, String token) {
        final EncryptedJWT jwe;
        try {
            jwe = EncryptedJWT.parse(token);
        } catch (ParseException ex) {
            throw new BadCredentialsException("unparseable jwe", ex);
        }
        try {
            jwe.decrypt(proc.decrypter());
        } catch (JOSEException | RuntimeException ex) {
            throw new BadCredentialsException("invalid jwe", ex);
        }
        if (proc.innerVerifier() == null) {
            return accept(proc.claimsVerifier(), proc.principal(), proc.authorities(), jwe.getHeader(), claims(jwe));
        }
        final SignedJWT inner;
        try {
            inner = SignedJWT.parse(jwe.getPayload().toString());
        } catch (ParseException ex) {
            throw new BadCredentialsException("inner payload is not a signed jwt", ex);
        }
        verify(inner, proc.innerVerifier(), "invalid inner token");
        return accept(proc.claimsVerifier(), proc.principal(), proc.authorities(), inner.getHeader(), claims(inner));
    }

    private static void verify(SignedJWT jws, JWSVerifier verifier, String reason) {
        final boolean verified;
        try {
            verified = jws.verify(verifier);
        } catch (JOSEException | RuntimeException ex) {
            throw new BadCredentialsException(reason, ex);
        }
        if (!verified) {
            throw new BadCredentialsException(reason + " signature");
        }
    }

    private static JWTClaimsSet claims(JWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException ex) {
            throw new BadCredentialsException("unparseable claims", ex);
        }
    }

    private static PrincipalAndAuthorities accept(JWTClaimsSetVerifier<SecurityContext> claimsVerifier, JwtPrincipalConverter principals, JwtAuthoritiesConverter authorities, Header header, JWTClaimsSet claims) {
        try {
            claimsVerifier.verify(claims, null);
        } catch (BadJWTException ex) {
            throw new BadCredentialsException("invalid claims", ex);
        }
        final var principal = principals.convert(header, claims);
        if (principal == null) {
            throw new BadCredentialsException("null principal");
        }
        return new PrincipalAndAuthorities(principal, authorities.convert(header, claims));
    }

    public record JwsProcessor(HeaderAndScheme hs, JwsMatcher matcher, JWSVerifier verifier, JWTClaimsSetVerifier<SecurityContext> claimsVerifier, JwtAuthoritiesConverter authorities, JwtPrincipalConverter principal) {

    }

    public record JweProcessor(HeaderAndScheme hs, JweMatcher matcher, JWEDecrypter decrypter, JWSVerifier innerVerifier, JWTClaimsSetVerifier<SecurityContext> claimsVerifier, JwtAuthoritiesConverter authorities, JwtPrincipalConverter principal) {

    }

}
