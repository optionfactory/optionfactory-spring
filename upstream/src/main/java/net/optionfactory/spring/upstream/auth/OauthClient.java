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

/// An upstream client of an oauth2 token endpoint, used by the oauth authenticators to obtain access
/// tokens.
///
/// Build it with [net.optionfactory.spring.upstream.UpstreamBuilder] and a JSON configuration, with
/// the token endpoint itself as base uri: every method posts a form to it. It alerts on remoting
/// errors and on error statuses, and its mock serves a bundled bearer token valid for 600 seconds.
///
/// The `authenticate` overloads send `params` as the form body and `headers` as request headers; the
/// default methods build the parameters of the standard grants, leaving out the `null` ones.
@Upstream("oauth-client")
@Upstream.AlertOnRemotingError
@Upstream.AlertOnResponse(STATUS_IS_ERROR)
@Upstream.Mock.DefaultContentType("application/json")
public interface OauthClient {

    /// @param params the form parameters
    /// @param headers the request headers
    /// @return the token endpoint response
    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam Map<String, ?> params, @RequestHeader Map<String, ?> headers);

    /// @param params the form parameters
    /// @param headers the request headers
    /// @return the token endpoint response
    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam MultiValueMap<String, ?> params, @RequestHeader MultiValueMap<String, ?> headers);

    /// @param params the form parameters
    /// @return the token endpoint response
    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam Map<String, ?> params);

    /// @param params the form parameters
    /// @return the token endpoint response
    @PostExchange(contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Upstream.Endpoint("token")
    @Upstream.Mock("oauth-token-response.json")
    JsonNode authenticate(@RequestParam MultiValueMap<String, ?> params);

    /// Executes the client-credentials grant, authenticating the client with `Basic` authentication
    /// (UTF-8 encoded).
    ///
    /// @param clientId the client id
    /// @param clientSecret the client secret
    /// @param scope the requested scope, or `null` to request none
    /// @return the token endpoint response
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

    /// Executes the resource-owner password grant, sending the client credentials, when given, as form
    /// parameters.
    ///
    /// @param clientId the client id, or `null` not to send one
    /// @param clientSecret the client secret, or `null` not to send one
    /// @param username the resource owner username
    /// @param password the resource owner password
    /// @return the token endpoint response
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

    /// Exchanges an authorization code for tokens, sending the client credentials, when given, as form
    /// parameters.
    ///
    /// @param code the authorization code
    /// @param redirectUri the redirect uri of the authorization request
    /// @param clientId the client id, or `null` not to send one
    /// @param clientSecret the client secret, or `null` not to send one
    /// @param codeVerifier the PKCE code verifier, or `null` when PKCE is not used
    /// @return the token endpoint response
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

    /// Exchanges a signed assertion for an access token using the RFC 7523
    /// JWT-bearer grant.
    ///
    /// @param assertion the serialized, JWS-signed assertion
    ///
    /// @return the token endpoint's response, carrying at least an
    /// `access_token` and its `expires_in`
    default JsonNode jwtBearer(String assertion) {
        return authenticate(Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion", assertion
        ));
    }

}
