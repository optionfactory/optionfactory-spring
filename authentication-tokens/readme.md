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

### JWE Authentication

Encrypted JWTs are supported in two modes, selected automatically from the configured decrypter.

**Symmetric** — the shared secret is the trust root; the payload is read as raw claims (no inner signature):

```java
c.jwe(jc -> {
    jc.decrypt(SHARED_AES_KEY);                       // AESDecrypter / DirectDecrypter
    jc.claims(Duration.ofSeconds(60), claims -> {
        claims.audience("example.com");
        claims.exact("iss", "my-issuer");
    });
    jc.principal("service-name");
    jc.authorities("ROLE_M2M");
});
```

**Asymmetric** — the token is encrypted to our public key, so the issuer is authenticated by a nested signed JWT (`JWE(JWS(claims))`):

```java
c.jwe(jc -> {
    jc.decrypt(recipientEcPrivateKey);                // ECDHDecrypter (encryption to us)
    jc.verify(issuerRsaPublicKey);                    // verifies the inner JWS (issuer authenticity)
    jc.claims(Duration.ofSeconds(60), claims -> {
        claims.audience("example.com");
        claims.exact("iss", "my-issuer");
    });
    jc.principal("service-name");
    jc.authorities("ROLE_M2M");
});
```

JWE only provides confidentiality, so an asymmetric decrypter (`ECDHDecrypter`/`RSADecrypter`) **requires** `verify(...)` — anyone holding the recipient's public key can encrypt, and only the inner signature proves the issuer. Symmetric decrypters read raw claims and rely on the secrecy of the shared key; do not set `verify(...)` unless you want the nested-JWS path. The issuer must produce asymmetric tokens as sign-then-encrypt.

