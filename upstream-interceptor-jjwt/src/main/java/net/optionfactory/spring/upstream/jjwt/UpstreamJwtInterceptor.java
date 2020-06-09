package net.optionfactory.spring.upstream.jjwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Function;
import net.optionfactory.spring.upstream.UpstreamInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;

public class UpstreamJwtInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final String jwtIssuer;
    private final String jwtSecret;
    private final String audience;
    private final Function<CTX, String> subjectFactory;
    private final SignatureAlgorithm algorithm;

    public UpstreamJwtInterceptor(String jwtIssuer, String jwtSecret, String audience, Function<CTX, String> subjectFactory, SignatureAlgorithm algorithm) {
        this.jwtIssuer = jwtIssuer;
        this.jwtSecret = jwtSecret;
        this.audience = audience;
        this.subjectFactory = subjectFactory;
        this.algorithm = algorithm;
    }

    @Override
    public HttpHeaders prepare(String upstreamId, CTX ctx, RequestEntity<?> entity) {
        final var headers = new HttpHeaders();
        final var claims = new HashMap<String, Object>();
        final var jwtIssuedAt = new Date();
        final var jwtExpiration = new Date(30 * 60 * 1000 + jwtIssuedAt.getTime());
        final String jwt = Jwts.builder()
                .setClaims(claims)
                .setSubject(subjectFactory.apply(ctx))
                .setAudience(audience)
                .setIssuer(jwtIssuer)
                .setIssuedAt(jwtIssuedAt)
                .setExpiration(jwtExpiration)
                .signWith(algorithm, jwtSecret)
                .compact();

        headers.set("Authorization", String.format("Bearer %s", jwt));
        return headers;
    }

}
