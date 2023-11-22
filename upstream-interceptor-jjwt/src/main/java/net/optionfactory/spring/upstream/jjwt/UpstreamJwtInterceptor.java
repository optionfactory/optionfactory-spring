package net.optionfactory.spring.upstream.jjwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Function;
import javax.crypto.SecretKey;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

public class UpstreamJwtInterceptor implements UpstreamHttpInterceptor {

    private final String jwtIssuer;
    private final SecretKey jwtSecret;
    private final String audience;
    private final Function<InvocationContext, String> subjectFactory;
    private final MacAlgorithm algorithm;

    public UpstreamJwtInterceptor(String jwtIssuer, SecretKey jwtSecret, String audience, Function<InvocationContext, String> subjectFactory, MacAlgorithm algorithm) {
        this.jwtIssuer = jwtIssuer;
        this.jwtSecret = jwtSecret;
        this.audience = audience;
        this.subjectFactory = subjectFactory;
        this.algorithm = algorithm;
    }

    @Override
    public ClientHttpResponse intercept(InvocationContext ctx, HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
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
        return execution.execute(request, body);
    }

}
