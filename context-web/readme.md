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

**The custom resolver must be named `customLocaleResolver`.** A bean named `localeResolver` is
deliberately *not* picked up: `WebMvcConfigurationSupport` already defines a bean with exactly
that name, so a conventional-named bean either collides with it or is overridden by it, and this
configuration could not reliably resolve it. Name the bean `customLocaleResolver` (or annotate it
with `@Qualifier("customLocaleResolver")`); when none is present the default
`AcceptHeaderLocaleResolver` applies and a WARN says so. Two custom resolvers fail startup.

Direct field access is the point of the annotation: use plain `@EnableWebMvc` when you do not
want it.


