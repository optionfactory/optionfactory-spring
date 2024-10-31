package net.optionfactory.spring.upstream.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import static net.optionfactory.spring.upstream.Upstream.AlertOnResponse.STATUS_IS_ERROR;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;

@Upstream("oauth-client")
@Upstream.AlertOnRemotingError
@Upstream.AlertOnResponse(STATUS_IS_ERROR)
@Upstream.Mock.DefaultContentType("application/json")
public interface OauthClient {

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam Map<String, ?> params, @RequestHeader Map<String, ?> headers);

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam MultiValueMap<String, ?> params, @RequestHeader MultiValueMap<String, ?> headers);

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam Map<String, ?> params);

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam MultiValueMap<String, ?> params);

    default JsonNode clientCredentials(String clientId, String clientSecret) {
        final var token = HttpHeaders.encodeBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);
        return authenticate(
                Map.of("grant_type", "client_credentials"),
                Map.of(HttpHeaders.AUTHORIZATION, String.format("Basic %s", token))
        );
    }

    default JsonNode password(String clientId, String username, String password) {
        return authenticate(Map.of(
                "grant_type", "password",
                "client_id", clientId,
                "username", username,
                "password", password
        ));
    }

    default JsonNode authorizationCode(String clientId, String clientSecret, String code, String redirectUri) {
        return authenticate(Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri
        ));
    }

}
