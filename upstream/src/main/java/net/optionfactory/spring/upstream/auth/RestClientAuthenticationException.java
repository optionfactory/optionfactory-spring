package net.optionfactory.spring.upstream.auth;

import org.springframework.web.client.RestClientException;

public class RestClientAuthenticationException extends RestClientException {

    public RestClientAuthenticationException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
