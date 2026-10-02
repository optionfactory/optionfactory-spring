# optionfactory-spring/context-web

Spring MVC configuration with direct field access binding and a custom locale resolver.

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

### What direct field access protects from, and what it costs

Spring's default data binder writes request parameters (query strings, forms: `@ModelAttribute`
and other complex handler arguments) through setters only. A DTO class with public fields and no
setters is left empty, without any binding error. Direct field access binds those fields. It does
not affect `@RequestBody`, `@RequestParam`, `@PathVariable` or responses.

The costs:

- **records cannot be bound from request parameters**: the request is answered `400` ("Field is
  not accessible"), and a class with a nested record property fails with a `500`. Plain
  `@EnableWebMvc` binds them correctly.
- **every field is bindable**, private ones without setters included: a request parameter named
  after an internal field writes it.

It can be dropped once every type bound from request parameters is a record or a JavaBean; types
read only from json bodies do not count. See [ADR 0011](../docs/decisions/0011-field-binding.md).


