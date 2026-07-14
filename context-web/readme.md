# optionfactory-spring/context-web

Property source configuration, conditional beans and WebMvc direct field access configuration.

## Maven

```xml
<dependency>
    <groupId>net.optionfactory.spring</groupId>
    <artifactId>context-web</artifactId>
</dependency>
```

## Usage

### EnableCustomWebMvc

To be used in place of `@EnableWebMvc`, enforcing access to bean fields instead of referencing getters and setters. It also supports a custom `LocaleResolver` if a bean named `customLocaleResolver` is present.

```java
@Configuration
@EnableCustomWebMvc
public class MyWebConfig {
}
```


