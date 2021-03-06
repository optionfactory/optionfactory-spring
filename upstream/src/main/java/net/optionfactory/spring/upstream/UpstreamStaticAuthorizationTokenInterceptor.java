package net.optionfactory.spring.upstream;

import org.springframework.http.HttpHeaders;

public class UpstreamStaticAuthorizationTokenInterceptor<T> implements UpstreamInterceptor<T> {

    private final String tokenType;
    private final String token;

    public UpstreamStaticAuthorizationTokenInterceptor(String tokenType, String token) {
        this.tokenType = tokenType;
        this.token = token;
    }

    @Override
    public HttpHeaders prepare(PrepareContext<T> prepare) {
        final var headers = new HttpHeaders();
        headers.set("Authorization", String.format("%s %s", tokenType, token));
        return headers;
    }

}
