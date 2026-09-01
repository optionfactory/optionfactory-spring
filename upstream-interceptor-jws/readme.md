# optionfactory-spring/upstream-interceptor-jws

`com.nimbusds:nimbus-jose-jwt` based authenticators for upstream clients.

Two flavors are available, for two different trust models:

| authenticator | trust model | when to use |
|---|---|---|
| `UpstreamJwsAuthenticator` | the server trusts your signing key directly | stateless per-request signed JWTs (no token endpoint involved) |
| `UpstreamJwtBearerGrantAuthenticator` | the server delegates to an OAuth2 authorization server | RFC 7523 jwt-bearer grant: exchange a signed assertion for an access token, e.g. Google service accounts |

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>upstream-interceptor-jws</artifactId>
</dependency>
```

The Google service-account example below also uses the `pem` module to parse
the key:

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>pem</artifactId>
</dependency>
```

## Per-request signed JWTs

`UpstreamJwsAuthenticator` signs a fresh JWT for every request and attaches it
as the `Authorization: Bearer` header value. Signed claims are
`iss`/`sub`/`aud`/`iat`/`exp` (with a `typ: JWT` header); the subject is
produced per invocation, so it can depend on the `InvocationContext`.

Symmetric HMAC signature:

```java
UpstreamBuilder.create(MyClient.class)
    .initializer(UpstreamJwsAuthenticator
        .builder(new MACSigner(jwtSecretBytes))
        .issuer("my-issuer")
        .audience("my-audience")
        .duration(Duration.ofMinutes(5))
        .subject(ctx -> "custom-subject")
        .algorithm(JWSAlgorithm.HS256)
        .build())
    .baseUri("https://api.example.com")
    // ...
    .build();
```

Asymmetric signature (RS256/RS384/RS512, ES256...):

```java
UpstreamJwsAuthenticator
    .builder(new RSASSASigner(rsaPrivateKey))
    .issuer("my-issuer")
    .audience("my-audience")
    .duration(Duration.ofMinutes(5))
    .subject(ctx -> "custom-subject")
    .algorithm(JWSAlgorithm.RS256)
    .build()
```

## RFC 7523 JWT-bearer grant

`UpstreamJwtBearerGrantAuthenticator` signs an assertion, exchanges it for an
access token at the authorization server's token endpoint (via the `upstream`
module's `OauthClient`), and caches the resulting access token until shortly
before its expiry. Assertions carry a `typ: JWT` header and
`iss`/`sub`/`aud`/`iat`/`exp`/`jti`/`scope` claims, where `aud` is the token
endpoint, `sub` defaults to `iss` (service accounts) and `jti` is a random
identifier for replay mitigation.

This is the flow Google uses for service accounts. A complete setup calling a
Google API (FCM, in this example):

```java
// the service account key file, as downloaded from the Google console
final var credentials = JsonMapper.builder().build().readTree(serviceAccountJson);
final var clientEmail = credentials.get("client_email").asString();
final var privateKeyPem = credentials.get("private_key").asString();
final var privateKey = Pem.privateKey(
        new ByteArrayInputStream(privateKeyPem.getBytes(StandardCharsets.UTF_8)),
        null);

// the token endpoint, as a regular upstream client
final var oauth = UpstreamBuilder.named(OauthClient.class, "google-auth")
        .requestFactoryHttpComponents(c -> {
        })
        .json(JsonMapper.builder().build())
        .baseUri("https://oauth2.googleapis.com/token")
        .build();

// exchanges signed assertions for cached access tokens
final var authenticator = UpstreamJwtBearerGrantAuthenticator
        .builder(oauth, new RSASSASigner(privateKey))
        .issuer(clientEmail)                              // the service account's client_email
        .audience("https://oauth2.googleapis.com/token")  // RFC 7523: aud is the token endpoint
        .scopes("https://www.googleapis.com/auth/firebase.messaging")
        .build();

return UpstreamBuilder.create(FcmClient.class)
        .initializer(authenticator)
        .requestFactoryHttpComponents(c -> {
        })
        .json(JsonMapper.builder().build())
        .baseUri("https://fcm.googleapis.com")
        .build();
```

```java
@Upstream("fcm")
@Upstream.Logging
@Upstream.Mock.DefaultContentType("application/json")
public interface FcmClient {

    @PostExchange("/v1/projects/{projectId}/messages:send")
    @Upstream.Endpoint("send")
    @Upstream.Mock("send.json")
    JsonNode send(@PathVariable String projectId, @RequestBody Map<String, Object> message);
}
```

Defaults and knobs (all optional):

*   `algorithm`: `RS256` (Google requires RSA for service accounts)
*   `assertionValidity`: one hour
*   `refreshMargin`: the access token is refreshed 60 seconds before the
    `expires_in` returned by the token endpoint
*   `subject`: overrides `sub`, for domain-wide delegation flows
*   `clock`: injectable for testing

The cache is thread-safe: concurrent requests share one access token and at
most one token exchange takes place per expiry window.

## Testing

Both authenticators are initializers, so they compose with the usual upstream
mocking support: build the `OauthClient` and the target client with
`requestFactoryMockIf(...)` and no real HTTP will be performed. `OauthClient`
ships with a built-in `oauth-token-response.json` mock resource, so mocked
contexts get a valid token response out of the box:

```java
final var oauth = UpstreamBuilder.named(OauthClient.class, "google-auth")
        .requestFactoryMockIf(isMock, c -> {
        })
        .requestFactoryHttpComponentsIf(!isMock, c -> {
        })
        .json(mapper)
        .baseUri("https://oauth2.googleapis.com/token")
        .build();
```
