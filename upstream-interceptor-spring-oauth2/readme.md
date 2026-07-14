# optionfactory-spring/upstream-interceptor-spring-oauth2

An [`OAuth2AuthorizedClientManager`](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/client/OAuth2AuthorizedClientManager.html) based interceptor for upstream clients.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>upstream-interceptor-spring-oauth2</artifactId>
</dependency>
```

## Usage

Register the `UpstreamSpringOAuthInterceptor` in your `UpstreamBuilder`:

```java
UpstreamBuilder.create(MyClient.class)
    .initializer(new UpstreamSpringOAuthInterceptor(
        oauth2AuthorizedClientManager,
        OAuth2AuthorizeRequest.withClientRegistrationId("my-registration-id")
            .principal("my-principal")
            .build()
    ))
    .baseUri("https://api.example.com")
    // ...
    .build();
```


