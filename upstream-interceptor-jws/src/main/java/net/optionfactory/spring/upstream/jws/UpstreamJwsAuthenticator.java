package net.optionfactory.spring.upstream.jws;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.function.Function;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates each request by signing a fresh JWT and attaching it as the
/// `Authorization: Bearer` header value.
///
/// Any nimbus `JWSSigner` can be used: a `MACSigner` for symmetric HMAC
/// signatures, or asymmetric signers such as `RSASSASigner` (RS256) or
/// `ESASSASigner` (ES256). The produced JWT carries a `typ: JWT` header and
/// `iss`/`sub`/`aud`/`iat`/`exp` claims.
public class UpstreamJwsAuthenticator implements UpstreamHttpRequestInitializer {

    private final String jwtIssuer;
    private final JWSSigner signer;
    private final String audience;
    private final Duration duration;
    private final Function<InvocationContext, String> subjectFactory;
    private final JWSAlgorithm algorithm;

    /// Creates an authenticator signing one JWT per request.
    ///
    /// @param jwtIssuer the `iss` claim, identifying this client
    /// @param signer the JWT signer
    /// @param audience the `aud` claim, identifying the target server
    /// @param duration the validity window of each signed JWT
    /// @param subjectFactory produces the `sub` claim from the invocation
    /// @param algorithm the JWS algorithm, matching the given signer
    public UpstreamJwsAuthenticator(String jwtIssuer, JWSSigner signer, String audience, Duration duration, Function<InvocationContext, String> subjectFactory, JWSAlgorithm algorithm) {
        this.jwtIssuer = Objects.requireNonNull(jwtIssuer, "jwtIssuer is required");
        this.signer = Objects.requireNonNull(signer, "signer is required");
        this.audience = Objects.requireNonNull(audience, "audience is required");
        this.duration = Objects.requireNonNull(duration, "duration is required");
        this.subjectFactory = Objects.requireNonNull(subjectFactory, "subjectFactory is required");
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm is required");
    }

    /// Creates a builder for an authenticator signing JWTs with `signer`.
    ///
    /// @param signer the JWT signer, e.g. a `MACSigner` or an `RSASSASigner`
    ///
    /// @return a builder requiring every property to be set
    public static Builder builder(JWSSigner signer) {
        return new Builder(signer);
    }

    /// Signs a fresh JWT for the request and attaches it as the
    /// `Authorization: Bearer` header value.
    ///
    /// @param ctx the upstream invocation, used to produce the `sub` claim
    /// @param request the request to authenticate
    @Override
    public void initialize(InvocationContext ctx, ClientHttpRequest request) {
        final var issuedAt = Instant.now();
        final var expiration = issuedAt.plus(duration);

        final var claims = new JWTClaimsSet.Builder()
                .subject(subjectFactory.apply(ctx))
                .audience(audience)
                .issuer(jwtIssuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiration))
                .build();

        final var jws = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims);
        try {
            jws.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", jws.serialize()));
    }

    /// Builder for [UpstreamJwsAuthenticator].
    public static class Builder {

        private final JWSSigner signer;
        private String issuer;
        private String audience;
        private Duration duration;
        private Function<InvocationContext, String> subjectFactory;
        private JWSAlgorithm algorithm;

        private Builder(JWSSigner signer) {
            this.signer = Objects.requireNonNull(signer, "signer is required");
        }

        /// Sets the JWT's `iss` claim, identifying this client.
        ///
        /// @param issuer the client identifier
        ///
        /// @return this builder
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /// Sets the JWT's `aud` claim, identifying the target server.
        ///
        /// @param audience the expected audience
        ///
        /// @return this builder
        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        /// Sets the validity window of each signed JWT.
        ///
        /// @param duration the token's lifetime
        ///
        /// @return this builder
        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /// Sets the factory producing the JWT's `sub` claim from the
        /// invocation.
        ///
        /// @param subjectFactory the subject factory
        ///
        /// @return this builder
        public Builder subject(Function<InvocationContext, String> subjectFactory) {
            this.subjectFactory = subjectFactory;
            return this;
        }

        /// Sets the signature algorithm, matching the given signer.
        ///
        /// @param algorithm the JWS algorithm
        ///
        /// @return this builder
        public Builder algorithm(JWSAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /// Builds the authenticator, requiring every property to be set.
        ///
        /// @return a new authenticator
        public UpstreamJwsAuthenticator build() {
            return new UpstreamJwsAuthenticator(issuer, signer, audience, duration, subjectFactory, algorithm);
        }

    }

}
