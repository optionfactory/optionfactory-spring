# optionfactory-spring/authentication-resource-server

BearerTokenResolvers with JWT Header introspection.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>authentication-resource-server</artifactId>
</dependency>
```

## Usage

Use `JwtTokenResolverAdapter` to resolve bearer tokens based on JWT header values (e.g., checking the Key ID `kid`):

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.oauth2ResourceServer(oauth2 -> oauth2
        .bearerTokenResolver(new JwtTokenResolverAdapter(header -> 
            "expected-kid".equals(header.toJSONObject().get("kid"))
        ))
        .jwt(Customizer.withDefaults())
    );
    // ...
    return http.build();
}
```
