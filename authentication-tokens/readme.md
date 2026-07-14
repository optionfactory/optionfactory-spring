# optionfactory-spring/authentication-tokens

Authentication via HTTP headers (opaque tokens, jws, jwe) for Spring Security.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>authentication-tokens</artifactId>
</dependency>
```

## Usage

Configure the header-based authentication in your Spring Security configuration:

```java
@Bean
public SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.with(HttpHeaderAuthentication.configurer(), c -> {
        // JWS Authentication
        c.jws(jc -> {
            jc.verify(HS256_KEY);
            jc.claims(Duration.ofSeconds(60), claims -> {
                claims.audience("example.com");
                claims.exact("iss", "my-issuer");
            });
            jc.principal("service-name");
            jc.authorities("ROLE_M2M");
        });
        
        // Opaque Bearer Token
        c.bearer("MY_SECRET_TOKEN", "principal-name", "ROLE_USER");
        
        // Custom Basic Authentication
        c.basic("username", "password", "principal-name", "ROLE_ADMIN");
    });
    // ...
    return http.build();
}
```

