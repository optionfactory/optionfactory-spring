package net.optionfactory.spring.upstream.auth;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.upstream.Upstream;
import static net.optionfactory.spring.upstream.Upstream.AlertOnResponse.STATUS_IS_ERROR;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.PostExchange;
import tools.jackson.databind.JsonNode;

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

    default JsonNode clientCredentials(String clientId, String clientSecret, @Nullable String scope) {
        final var params = Stream.of(
                new SimpleEntry<>("grant_type", "client_credentials"),
                new SimpleEntry<>("scope", scope)
        )
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        final var token = HttpHeaders.encodeBasicAuth(clientId, clientSecret, StandardCharsets.UTF_8);
        return authenticate(
                params,
                Map.of(HttpHeaders.AUTHORIZATION, String.format("Basic %s", token))
        );
    }

    default JsonNode password(@Nullable String clientId, @Nullable String clientSecret, String username, String password) {
        final var params = Stream.of(
                new SimpleEntry<>("grant_type", "password"),
                new SimpleEntry<>("username", username),
                new SimpleEntry<>("password", password),
                new SimpleEntry<>("client_id", clientId),
                new SimpleEntry<>("client_secret", clientSecret)
        )
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

        return authenticate(params);
    }

    default JsonNode authorizationCode(String code, String redirectUri, @Nullable String clientId, @Nullable String clientSecret, @Nullable String codeVerifier) {
        final var params = Stream.of(
                new SimpleEntry<>("grant_type", "authorization_code"),
                new SimpleEntry<>("code", code),
                new SimpleEntry<>("redirect_uri", redirectUri),
                new SimpleEntry<>("client_id", clientId),
                new SimpleEntry<>("client_secret", clientSecret),
                new SimpleEntry<>("code_verifier", codeVerifier)
        )
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
        return authenticate(params);
    }

}
