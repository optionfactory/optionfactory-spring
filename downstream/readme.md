# optionfactory-spring/downstream

Annotations to mark controller methods and DTOs for client code generation (Java DTOs or TypeScript types) via `downstream-maven-plugin`.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>downstream</artifactId>
</dependency>
```

## Usage

Annotate your controller methods to include them in the client generation:

```java
@RestController
public class MyController {

    @PostMapping("/api/orders")
    @Downstream.Method(clients = "web-frontend")
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        // ...
    }
}
```

Use `@Downstream.Ignore` to exclude specific types or fields, and `@Downstream.Rename` to change the name of a generated type.


