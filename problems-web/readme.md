# optionfactory-spring/problems-web

REST exception resolver for reporting errors in API responses (e.g., validation errors) using the `problems` module types.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>problems-web</artifactId>
</dependency>
```

Note: `net.optionfactory.spring:problems` is a transitive dependency.

## Usage

Configure the exception resolvers in your `WebMvcConfigurer`:

```java
@Override
public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    ExceptionResolvers.configurer(resolvers)
            .rest(jsonMapper) // Registers RestExceptionResolver
            .configure();
}
```

This will automatically map standard Spring exceptions (like `MethodArgumentNotValidException`) and custom `Failure` exceptions to a unified JSON error response.

