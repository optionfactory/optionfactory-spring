package net.optionfactory.spring.upstream.auth;

import org.springframework.web.client.RestClientException;

public class RestClientAuthenticationException extends RestClientException {

    public RestClientAuthenticationException(String upstream, String endpoint, Throwable cause) {
        super(String.format("Authentication failed for %s:%s", upstream, endpoint), cause);
    }

}
