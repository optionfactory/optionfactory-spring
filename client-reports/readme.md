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

The configured `reportUri` is matched against the request path with the deployment's context
path stripped, so the same configuration works at the root and under e.g. `/app`. Bodies larger
than `maxBodySize` are truncated and reported as an `"unparseable report"` text node.

### Who can post reports

The endpoint is open by design: it answers before csrf protection, authentication and
authorization run, so it receives reports from anonymous pages and from clients whose session
expired. A report carries the principal of the session cookie sent with it (`null` for principals
established per request, such as bearer tokens), and must never be trusted as an action of that
principal. Session cookies marked `SameSite=Lax` or `Strict`, the embedded tomcat default being
`Lax`, are not sent with cross-site POSTs, so another site cannot make a visitor's browser post a
report attributed to the visitor; with `SameSite=None` it can. The body is capped by
`maxBodySize`, but there is no rate limit.
