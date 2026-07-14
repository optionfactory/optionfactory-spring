# optionfactory-spring/content-security-policies

Nonce Based [Strict Content Security Policy](https://web.dev/articles/strict-csp) for Spring Security.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>content-security-policies</artifactId>
</dependency>
```

## Usage

### 1. Configure Spring Security

```java
@Bean
public SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.with(StrictContentSecurityPolicy.configurer(), c -> {
        c.mode(ContentSecurityPolicyMode.ENFORCE); // or REPORT
        c.reportUri("/api/csp-report");
    });
    return http.build();
}
```

### 2. Configure Spring MVC Interceptor

To make the nonce available in your views (e.g., Thymeleaf):

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(StrictContentSecurityPolicy.addNonceToModel());
    }
}
```

### 3. Use in Thymeleaf

```html
<script th:nonce="${csp.nonce}">
    console.log("This script is allowed by CSP");
</script>
```
