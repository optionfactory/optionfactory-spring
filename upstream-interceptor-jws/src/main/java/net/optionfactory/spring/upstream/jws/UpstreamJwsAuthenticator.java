package net.optionfactory.spring.upstream.jws;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.Function;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.http.client.ClientHttpRequest;

public class UpstreamJwsAuthenticator implements UpstreamHttpRequestInitializer {

    private final String jwtIssuer;
    private final MACSigner signer;
    private final String audience;
    private final Function<InvocationContext, String> subjectFactory;
    private final JWSAlgorithm algorithm;

    public UpstreamJwsAuthenticator(String jwtIssuer, byte[] jwtSecret, String audience, Function<InvocationContext, String> subjectFactory, JWSAlgorithm algorithm) {
        this.jwtIssuer = jwtIssuer;
        this.audience = audience;
        this.subjectFactory = subjectFactory;
        this.algorithm = algorithm;
        try {
            this.signer = new MACSigner(jwtSecret);
        } catch (KeyLengthException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void initialize(InvocationContext ctx, ClientHttpRequest request) {
        final var issuedAt = Instant.now();
        final var expiration = issuedAt.plus(30, ChronoUnit.MINUTES);
        
        final var claims = new JWTClaimsSet.Builder()
                .subject(subjectFactory.apply(ctx))
                .audience(audience)
                .issuer(jwtIssuer)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiration))
                .build();

        final var jws = new SignedJWT(new JWSHeader(algorithm), claims);
        try {
            jws.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        request.getHeaders().add("Authorization", String.format("Bearer %s", jws.serialize()));        
    }

}
