# optionfactory-spring/authentication

Support for unifying different Principal types in Spring Security into a single custom type.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>authentication</artifactId>
</dependency>
```

## Usage

Configure the principal coalescing in your Spring Security configuration:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.with(Principals.coalescing(CustomPrincipal.class), c -> {
        c.principal(UserDetails.class, (auth, user) -> new CustomPrincipal(user.getUsername()));
        // Map other principal types as needed
    });
    // ...
    return http.build();
}
```

Now you can inject your custom principal in controller methods:

```java
@GetMapping("/api/me")
public String me(@AuthenticationPrincipal CustomPrincipal principal) {
    return principal.name();
}
```
