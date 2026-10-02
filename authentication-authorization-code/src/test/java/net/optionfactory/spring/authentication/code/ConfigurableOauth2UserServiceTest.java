package net.optionfactory.spring.authentication.code;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class ConfigurableOauth2UserServiceTest {

    private static final ClientRegistration REGISTRATION = ClientRegistration.withRegistrationId("idp")
            .clientId("client-id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://app.example.com/login/oauth2/code/idp")
            .authorizationUri("https://idp.example.com/authorize")
            .tokenUri("https://idp.example.com/token")
            .scope("openid")
            .build();

    private static OidcUserRequest request(Map<String, Object> claims) {
        final var now = Instant.now();
        final var idToken = new OidcIdToken("id-token", now, now.plusSeconds(300), claims);
        final var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(300));
        return new OidcUserRequest(REGISTRATION, accessToken, idToken);
    }

    private static ConfigurableOauth2UserService<OidcUser> service(StubClientHttpRequestFactory idp) {
        return new ConfigurableOauth2UserService<>(idp, event -> {
        }, (authorities, user) -> new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo()));
    }

    private static Set<String> names(OidcUser user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    public void groupsBecomeRoleGroupAuthorities() {
        final var user = service(new StubClientHttpRequestFactory(HttpStatus.OK, "{}"))
                .loadUser(request(Map.of("sub", "alice", "groups", List.of("sales-team", "admin"))));

        Assertions.assertTrue(names(user).containsAll(Set.of("ROLE_GROUP_SALES_TEAM", "ROLE_GROUP_ADMIN")), "each group becomes a ROLE_GROUP_ authority, upper-cased and with dashes turned into underscores");
    }

    @Test
    public void theDelegatesAuthoritiesAreKept() {
        final var user = service(new StubClientHttpRequestFactory(HttpStatus.OK, "{}"))
                .loadUser(request(Map.of("sub", "alice", "groups", List.of("admin"))));

        Assertions.assertTrue(names(user).contains("OIDC_USER"), "the authorities spring grants are kept besides the group ones");
    }

    @Test
    public void aUserWithoutGroupsGetsTheDelegatesAuthoritiesOnly() {
        final var user = service(new StubClientHttpRequestFactory(HttpStatus.OK, "{}"))
                .loadUser(request(Map.of("sub", "alice")));

        Assertions.assertTrue(names(user).stream().noneMatch(a -> a.startsWith("ROLE_GROUP_")), "no groups claim grants no group authority");
        Assertions.assertEquals("alice", user.getSubject(), "the user is built from the id token");
    }

    @Test
    public void theUserFactoryBuildsTheApplicationUser() {
        final var service = new ConfigurableOauth2UserService<>(new StubClientHttpRequestFactory(HttpStatus.OK, "{}"), event -> {
        }, (authorities, user) -> new DefaultOidcUser(Set.of(), user.getIdToken()));

        final var user = service.loadUser(request(Map.of("sub", "alice", "groups", List.of("admin"))));

        Assertions.assertTrue(user.getAuthorities().isEmpty(), "whatever the factory returns is the user, its authorities included");
    }

    @Test
    public void theUserInfoIsFetchedThroughTheConfiguredRequestFactory() {
        final var registration = ClientRegistration.withClientRegistration(REGISTRATION)
                .userInfoUri("https://idp.example.com/userinfo")
                .userNameAttributeName("sub")
                .build();
        final var now = Instant.now();
        final var idToken = new OidcIdToken("id-token", now, now.plusSeconds(300), Map.of("sub", "alice"));
        final var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", now, now.plusSeconds(300), Set.of("profile"));
        final var idp = new StubClientHttpRequestFactory(HttpStatus.OK, """
                {"sub": "alice", "groups": ["from-userinfo"]}
                """);

        final var user = service(idp).loadUser(new OidcUserRequest(registration, accessToken, idToken));

        Assertions.assertEquals(1, idp.requests.size(), "the userinfo endpoint is called through the configured request factory");
        Assertions.assertEquals("https://idp.example.com/userinfo", idp.requests.get(0).getURI().toString(), "the userinfo endpoint is the registration's");
        Assertions.assertTrue(names(user).contains("ROLE_GROUP_FROM_USERINFO"), "groups returned by the userinfo endpoint grant authorities too");
    }
}
