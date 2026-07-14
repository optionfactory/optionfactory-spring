# optionfactory-spring/applications-web-tomcat

Embedded Tomcat + Spring MVC configuration simplified through the `@EmbeddedTomcatWebMvcApplication` annotation.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>applications-web-tomcat</artifactId>
</dependency>
```

## Usage

Use the `@EmbeddedTomcatWebMvcApplication` annotation on your main configuration class:

```java
@EmbeddedTomcatWebMvcApplication(
    port = "${server.port}", 
    sameSite = "LAX"
)
@Configuration
public class MyWebApp {
    public static void main(String[] args) {
        SpringApplication.run(MyWebApp.class, args);
    }
}
```

This annotation automatically configures:
- Embedded Tomcat on the specified port.
- `@EnableCustomWebMvc` (direct field access).
- `ApplicationPropertiesConfig` (standard property sources).
- Multipart support with configurable limits.
- Classpath scanning for `@Controller` and `@ControllerAdvice`.


