package net.optionfactory.spring.upstream.jws;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.auth.OauthClient;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;

/// Authenticates requests using the RFC 7523 JWT-bearer grant: a JWS-signed
/// assertion is exchanged for an access token at the authorization server's
/// token endpoint (e.g. Google's oauth2.googleapis.com/token using a service
/// account's private key), and the returned access token is cached until
/// shortly before its expiry.
///
/// Assertions carry a `typ: JWT` header and `iss`/`sub`/`aud`/`iat`/`exp`/
/// `jti`/`scope` claims, where `aud` is the token endpoint, `sub` defaults to
/// `iss` (service accounts) and `jti` is a random identifier for replay
/// mitigation.
///
/// The cache is thread-safe: concurrent requests share one access token and at
/// most one token exchange takes place per expiry window. An access token is
/// retired [refreshMargin] seconds before the `expires_in` declared by the
/// token endpoint, absorbing clock skew and in-flight request latency.
public class UpstreamJwtBearerGrantAuthenticator implements UpstreamHttpRequestInitializer {

    /// Default signature algorithm for assertions.
    public static final JWSAlgorithm DEFAULT_ALGORITHM = JWSAlgorithm.RS256;

    /// Default validity of signed assertions.
    public static final Duration DEFAULT_ASSERTION_VALIDITY = Duration.ofHours(1);

    /// Default anticipation of the access token's expiry before refresh.
    public static final Duration DEFAULT_REFRESH_MARGIN = Duration.ofSeconds(60);

    /// The `grant_type` value of the RFC 7523 JWT-bearer grant.
    public static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final OauthClient client;
    private final JWSSigner signer;
    private final JWSAlgorithm algorithm;
    private final String issuer;
    private final String subject;
    private final String audience;
    private final List<String> scopes;
    private final Duration assertionValidity;
    private final Duration refreshMargin;
    private final Clock clock;

    private volatile CachedAccessToken cached;

    private record CachedAccessToken(String value, Instant refreshAt) {

    }

    private UpstreamJwtBearerGrantAuthenticator(Builder builder) {
        this.client = builder.client;
        this.signer = builder.signer;
        this.algorithm = builder.algorithm;
        this.issuer = Objects.requireNonNull(builder.issuer, "issuer is required");
        this.subject = builder.subject;
        this.audience = Objects.requireNonNull(builder.audience, "audience is required");
        this.scopes = List.copyOf(builder.scopes);
        this.assertionValidity = builder.assertionValidity;
        this.refreshMargin = builder.refreshMargin;
        this.clock = builder.clock;
    }

    /// Creates a builder for an authenticator exchanging assertions signed by
    /// `signer` at `client`'s token endpoint.
    ///
    /// @param client the token endpoint, as an upstream-built `OauthClient`
    /// @param signer the assertion signer, e.g. an `RSASSASigner` wrapping the
    /// service account's private key
    ///
    /// @return a builder requiring at least [Builder#issuer] and
    /// [Builder#audience]
    public static Builder builder(OauthClient client, JWSSigner signer) {
        return new Builder(client, signer);
    }

    /// Attaches the cached access token, exchanging a fresh assertion for one
    /// when none is cached or the cached one is about to expire.
    ///
    /// @param invocation the upstream invocation, unused
    /// @param request the request to authenticate
    @Override
    public void initialize(InvocationContext invocation, ClientHttpRequest request) {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, String.format("Bearer %s", accessToken()));
    }

    /// Returns a valid access token, exchanging a signed assertion at the
    /// token endpoint when the cached token is missing or about to expire.
    ///
    /// @return the access token, to be presented as a bearer token
    public synchronized String accessToken() {
        final var now = clock.instant();
        final var token = cached;
        if (token != null && now.isBefore(token.refreshAt())) {
            return token.value();
        }
        final var issuedAt = clock.instant();
        final var response = client.jwtBearer(newAssertion(issuedAt));
        final var tokenNode = response.path("access_token");
        if (tokenNode.isMissingNode() || tokenNode.isNull()) {
            throw new IllegalStateException("jwt-bearer token response does not carry an access_token");
        }
        final var expiresIn = response.path("expires_in").asLong(DEFAULT_ASSERTION_VALIDITY.toSeconds());
        final var refreshAt = issuedAt.plusSeconds(expiresIn).minus(refreshMargin);
        cached = new CachedAccessToken(tokenNode.asString(), refreshAt);
        return cached.value();
    }

    private String newAssertion(Instant issuedAt) {
        final var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject != null ? subject : issuer)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(assertionValidity)))
                .jwtID(UUID.randomUUID().toString());
        if (!scopes.isEmpty()) {
            claims.claim("scope", String.join(" ", scopes));
        }
        final var jws = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims.build());
        try {
            jws.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("cannot sign jwt-bearer assertion", ex);
        }
        return jws.serialize();
    }

    /// Builder for [UpstreamJwtBearerGrantAuthenticator].
    public static class Builder {

        private final OauthClient client;
        private final JWSSigner signer;
        private String issuer;
        private String subject;
        private String audience;
        private final List<String> scopes = new ArrayList<>();
        private JWSAlgorithm algorithm = DEFAULT_ALGORITHM;
        private Duration assertionValidity = DEFAULT_ASSERTION_VALIDITY;
        private Duration refreshMargin = DEFAULT_REFRESH_MARGIN;
        private Clock clock = Clock.systemUTC();

        private Builder(OauthClient client, JWSSigner signer) {
            this.client = Objects.requireNonNull(client, "client is required");
            this.signer = Objects.requireNonNull(signer, "signer is required");
        }

        /// Sets the assertion's `iss` claim: the client identity the token
        /// endpoint authenticates, e.g. a Google service account's
        /// `client_email`.
        ///
        /// @param issuer the client identifier
        ///
        /// @return this builder
        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        /// Sets the assertion's `sub` claim, overriding the `iss`-derived
        /// default: use it for domain-wide delegation, where a service
        /// account impersonates a domain user.
        ///
        /// @param subject the impersonated identity
        ///
        /// @return this builder
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /// Sets the assertion's `aud` claim: the token endpoint itself, as
        /// required by RFC 7523.
        ///
        /// @param audience the token endpoint URI
        ///
        /// @return this builder
        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        /// Adds the given scopes to the assertion's `scope` claim, space
        /// separated as required by RFC 7523.
        ///
        /// @param scopes the scopes to request
        ///
        /// @return this builder
        public Builder scopes(String... scopes) {
            this.scopes.addAll(List.of(scopes));
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

        /// Sets how long signed assertions remain valid.
        ///
        /// @param assertionValidity the assertion's validity window
        ///
        /// @return this builder
        public Builder assertionValidity(Duration assertionValidity) {
            this.assertionValidity = assertionValidity;
            return this;
        }

        /// Sets how long before the access token's declared `expires_in` the
        /// token is refreshed, absorbing clock skew and in-flight request
        /// latency.
        ///
        /// @param refreshMargin the anticipated expiry window
        ///
        /// @return this builder
        public Builder refreshMargin(Duration refreshMargin) {
            this.refreshMargin = refreshMargin;
            return this;
        }

        /// Sets the clock used for assertion timestamps and cache expiry.
        ///
        /// @param clock the time source
        ///
        /// @return this builder
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /// Builds the authenticator, requiring `issuer` and `audience` to be
        /// set.
        ///
        /// @return a new authenticator
        public UpstreamJwtBearerGrantAuthenticator build() {
            return new UpstreamJwtBearerGrantAuthenticator(this);
        }

    }

}
