package net.optionfactory.spring.upstreamlegacy;

import net.optionfactory.spring.upstreamlegacy.UpstreamPort.Hints;
import org.springframework.http.HttpHeaders;

public class UpstreamLegacyStaticAuthorizationTokenInterceptor<T> implements UpstreamInterceptor<T> {

    private final String tokenType;
    private final String token;

    public UpstreamLegacyStaticAuthorizationTokenInterceptor(String tokenType, String token) {
        this.tokenType = tokenType;
        this.token = token;
    }

    @Override
    public HttpHeaders prepare(Hints<T> hints, PrepareContext<T> prepare) {
        final var headers = new HttpHeaders();
        headers.set("Authorization", String.format("%s %s", tokenType, token));
        return headers;
    }

}
