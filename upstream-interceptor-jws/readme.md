# optionfactory-spring/upstream-interceptor-jws

A `com.nimbusds:nimbus-jose-jwt` based JWS interceptor for upstream clients.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>upstream-interceptor-jws</artifactId>
</dependency>
```

## Usage

Register the `UpstreamJwsAuthenticator` as an initializer in your `UpstreamBuilder`:

```java
UpstreamBuilder.create(MyClient.class)
    .initializer(new UpstreamJwsAuthenticator(
        "my-issuer", 
        jwtSecretBytes, 
        "my-audience", 
        Duration.ofMinutes(5), 
        ctx -> "custom-subject", 
        JWSAlgorithm.HS256
    ))
    .baseUri("https://api.example.com")
    // ...
    .build();
```


