# optionfactory-spring/client-reports

Server-side logging and event publishing for client-side errors.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>client-reports</artifactId>
</dependency>
```

## Usage

Configure the client error reporter in your Spring Security configuration:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.with(ClientErrors.configurer(), c -> c
        .reportUri("/api/client-errors")
        .maxBodySize(65536)
        .log(true)
    );
    // ...
    return http.build();
}
```

Clients can then POST JSON error reports to `/api/client-errors`.


