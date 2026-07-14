# optionfactory-spring/authentication-authorization-code

Support for overriding components used in `org.springframework.security.oauth2.client`, and implementation of `OidcRelyingPartyInitiatedLogoutHandler`.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>authentication-authorization-code</artifactId>
</dependency>
```

## Usage

### OIDC Logout

Configure the OIDC logout handler in your Spring Security configuration:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.logout(logout -> logout
        .logoutSuccessHandler(new OidcRelyingPartyInitiatedLogoutHandler(
            URI.create("https://idp.example.com/logout"),
            URI.create("https://myapp.example.com/")
        ))
    );
    // ...
    return http.build();
}
```

### Configurable Components

The module provides components that allow using a custom `RestOperations` for OAuth2/OIDC:
- `ConfigurableAuthorizationCodeTokenResponseClient`
- `ConfigurableOauth2UserService`
- `ConfigurableOidcIdTokenDecoderFactory`


