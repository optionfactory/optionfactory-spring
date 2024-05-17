package net.optionfactory.spring.upstream.auth;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.Upstream;
import static net.optionfactory.spring.upstream.Upstream.FaultOnResponse.STATUS_IS_ERROR;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;

@Upstream("oauth-client")
@Upstream.FaultOnRemotingError
@Upstream.FaultOnResponse(STATUS_IS_ERROR)
@Upstream.Mock.DefaultContentType("application/json")
public interface OauthClient {

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(
            @RequestParam("grant_type") String grantType,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );

    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("username") String username,
            @RequestParam("password") String password
    );

    default JsonNode clientCredentials(String clientId, String clientSecret) {
        final var token = HttpHeaders.encodeBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);
        return authenticate("client_credentials", String.format("Basic %s", token));
    }

    default JsonNode password(String clientId, String username, String password) {
        return authenticate("password", clientId, username, password);
    }

}
