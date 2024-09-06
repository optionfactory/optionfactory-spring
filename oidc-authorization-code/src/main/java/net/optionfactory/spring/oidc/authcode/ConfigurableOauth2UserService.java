package net.optionfactory.spring.oidc.authcode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.RestTemplate;

/*
 * so we can customize the RestTemplate
**/
public class ConfigurableOauth2UserService<U extends OidcUser> implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate;
    private final BiFunction<Set<GrantedAuthority>, OidcUser, U> userFactory;

    public ConfigurableOauth2UserService(ClientHttpRequestFactory httpRequestFactory, ApplicationEventPublisher events, BiFunction<Set<GrantedAuthority>, OidcUser, U> userFactory) {
        final var oauth2RestTemplate = new RestTemplate(httpRequestFactory);
        oauth2RestTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        final var defaultOAuth2UserService = new DefaultOAuth2UserService();
        defaultOAuth2UserService.setRestOperations(oauth2RestTemplate);
        this.delegate = new OidcUserService();
        this.delegate.setOauth2UserService(defaultOAuth2UserService);
        this.userFactory = userFactory;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        final var oidcUser = delegate.loadUser(userRequest);
        final var augmentedAuthorities = new HashSet<GrantedAuthority>();
        augmentedAuthorities.addAll(oidcUser.getAuthorities());
        final List<String> groups = oidcUser.getAttribute("groups");
        if (groups != null) {
            final var additionalAuthorities = groups.stream()
                    .map(g -> String.format("ROLE_GROUP_%s", g.toUpperCase().replace("-", "_")))
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
            augmentedAuthorities.addAll(additionalAuthorities);
        }
        return userFactory.apply(augmentedAuthorities, oidcUser);
    }
}
