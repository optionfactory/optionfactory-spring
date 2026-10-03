package net.optionfactory.spring.authentication.code;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.RestTemplate;

/// Loads the OIDC user at login, as spring's `OidcUserService` does, with two additions: the
/// userinfo endpoint is called through a configurable `ClientHttpRequestFactory` (so that proxies,
/// timeouts and tls settings apply to it too), and the user's groups become authorities.
///
/// Each value of the `groups` attribute (from the id token or the userinfo response) is granted as
/// `ROLE_GROUP_<GROUP>`, upper-cased independently of the default locale and with `-` replaced by
/// `_`: `sales-team` grants `ROLE_GROUP_SALES_TEAM`, so that `hasRole("GROUP_SALES_TEAM")` checks it.
/// They are added to the authorities spring grants (`OIDC_USER` and the access token's `SCOPE_`
/// ones), and the result is handed to the user factory, which builds the application's own user.
/// A `groups` attribute that is not a list of strings fails the login as an authentication failure,
/// handled by the login's failure handler like any other.
///
/// ```java
/// http.oauth2Login(login -> login.userInfoEndpoint(u -> u.oidcUserService(
///         new ConfigurableOauth2UserService<>(requestFactory, (authorities, oidcUser) -> new AppUser(authorities, oidcUser)))));
/// ```
///
/// @param <U> the application's user type
public class ConfigurableOauth2UserService<U extends OidcUser> implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String INVALID_USER_INFO_RESPONSE_ERROR_CODE = "invalid_user_info_response";

    private final OidcUserService delegate;
    private final BiFunction<Set<GrantedAuthority>, OidcUser, U> userFactory;

    /// @param httpRequestFactory the factory the userinfo endpoint is called with
    /// @param userFactory builds the application user from the augmented authorities and the user
    /// spring loaded
    public ConfigurableOauth2UserService(ClientHttpRequestFactory httpRequestFactory, BiFunction<Set<GrantedAuthority>, OidcUser, U> userFactory) {
        final var oauth2RestTemplate = new RestTemplate(httpRequestFactory);
        oauth2RestTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        final var defaultOAuth2UserService = new DefaultOAuth2UserService();
        defaultOAuth2UserService.setRestOperations(oauth2RestTemplate);
        this.delegate = new OidcUserService();
        this.delegate.setOauth2UserService(defaultOAuth2UserService);
        this.userFactory = userFactory;
    }

    /// @param userRequest the tokens received at login, and the registration they are for
    /// @return the user built by the user factory
    /// @throws OAuth2AuthenticationException when the user cannot be loaded, e.g. when the
    /// userinfo endpoint fails or answers for another subject, and with the
    /// `invalid_user_info_response` error code when `groups` is not a list of strings
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        final var oidcUser = delegate.loadUser(userRequest);
        final var augmentedAuthorities = new HashSet<GrantedAuthority>();
        augmentedAuthorities.addAll(oidcUser.getAuthorities());
        final Object groups = oidcUser.getAttribute("groups");
        if (groups != null) {
            if (!(groups instanceof List<?> list) || !list.stream().allMatch(String.class::isInstance)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(INVALID_USER_INFO_RESPONSE_ERROR_CODE, "the groups attribute is not a list of strings", null));
            }
            final var additionalAuthorities = list.stream()
                    .map(String.class::cast)
                    .map(g -> String.format("ROLE_GROUP_%s", g.toUpperCase(Locale.ROOT).replace("-", "_")))
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
            augmentedAuthorities.addAll(additionalAuthorities);
        }
        return userFactory.apply(augmentedAuthorities, oidcUser);
    }
}
