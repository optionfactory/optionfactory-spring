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
        c.jws(ClaimsPolicy.issuer("my-issuer").audience("example.com"), jc -> {
            jc.verify(HS256_KEY);
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

### Claims policies

Every `jws(...)` and `jwe(...)` states, as its first argument, which claims a token must carry to
be accepted:

- `ClaimsPolicy.issuer(...)` and `ClaimsPolicy.audience(...)` start a **standard** policy: the token
  must carry an `exp` and not have expired, and it must name the issuer and/or one of the audiences.
  A standard policy can only be started from one of the two, so a token issued for another service,
  by the same issuer or by anyone else holding the same key, never passes it.
- `ClaimsPolicy.permissive()` checks `exp` and `nbf` only when the token carries them, and neither
  issuer nor audience. It is meant for tokens that carry none of these, typically issued by a third
  party, and can still pin the claims such a token does carry:
  `ClaimsPolicy.permissive().exact("client_id", "acme")`.
- `ClaimsPolicy.custom(verifier)` delegates to any Nimbus `JWTClaimsSetVerifier`.

Policies are immutable, each refinement returning a new one, so a policy can be defined once and
shared; the clock skew tolerated on `exp` and `nbf` defaults to 60 seconds and is changed with
`clockSkew(...)`.

A signing key shared by several audiences, such as an identity provider's public key, is exactly the
case a permissive policy does not protect: every token that key signs, for any audience, is accepted.

### JWE Authentication

Encrypted JWTs are supported in two modes, selected automatically from the configured decrypter.

**Symmetric**: the shared secret is the trust root; the payload is read as raw claims (no inner signature):

```java
c.jwe(ClaimsPolicy.issuer("my-issuer").audience("example.com"), jc -> {
    jc.decrypt(SHARED_AES_KEY);                       // AESDecrypter / DirectDecrypter
    jc.principal("service-name");
    jc.authorities("ROLE_M2M");
});
```

**Asymmetric**: the token is encrypted to our public key, so the issuer is authenticated by a nested signed JWT (`JWE(JWS(claims))`):

```java
c.jwe(ClaimsPolicy.issuer("my-issuer").audience("example.com"), jc -> {
    jc.decrypt(recipientEcPrivateKey);                // ECDHDecrypter (encryption to us)
    jc.verify(issuerRsaPublicKey);                    // verifies the inner JWS (issuer authenticity)
    jc.principal("service-name");
    jc.authorities("ROLE_M2M");
});
```

JWE only provides confidentiality, so an asymmetric decrypter (`ECDHDecrypter`/`RSADecrypter`) **requires** `verify(...)`, because anyone holding the recipient's public key can encrypt, and only the inner signature proves the issuer. Symmetric decrypters read raw claims and rely on the secrecy of the shared key; do not set `verify(...)` unless you want the nested-JWS path. The issuer must produce asymmetric tokens as sign-then-encrypt.

