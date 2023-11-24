package net.optionfactory.spring.upstream.jjwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Function;
import javax.crypto.SecretKey;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor.InvocationContext;
import net.optionfactory.spring.upstream.UpstreamHttpRequestInitializer;
import org.springframework.http.client.ClientHttpRequest;

public class UpstreamJwtAuthenticator implements UpstreamHttpRequestInitializer {

    private final String jwtIssuer;
    private final SecretKey jwtSecret;
    private final String audience;
    private final Function<InvocationContext, String> subjectFactory;
    private final MacAlgorithm algorithm;

    public UpstreamJwtAuthenticator(String jwtIssuer, SecretKey jwtSecret, String audience, Function<InvocationContext, String> subjectFactory, MacAlgorithm algorithm) {
        this.jwtIssuer = jwtIssuer;
        this.jwtSecret = jwtSecret;
        this.audience = audience;
        this.subjectFactory = subjectFactory;
        this.algorithm = algorithm;
    }

    @Override
    public void initialize(InvocationContext ctx, ClientHttpRequest request) {
        final var claims = new HashMap<String, Object>();
        final var jwtIssuedAt = new Date();
        final var jwtExpiration = new Date(30 * 60 * 1000 + jwtIssuedAt.getTime());
        final String jwt = Jwts.builder()
                .claims(claims)
                .subject(subjectFactory.apply(ctx))
                .audience().single(audience)
                .issuer(jwtIssuer)
                .issuedAt(jwtIssuedAt)
                .expiration(jwtExpiration)
                .signWith(jwtSecret, algorithm)
                .compact();

        request.getHeaders().set("Authorization", String.format("Bearer %s", jwt));
    }

}
