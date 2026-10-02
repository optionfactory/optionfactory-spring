# 0001. Opt-in by explicit configuration, no auto-configuration

Status: accepted

## Context

Auto-configuration trades an application's knowledge of its own wiring for a
shorter setup: a jar on the classpath is enough to register beans, filters and
converters, and what is active is decided by conditions the application never
reads. The cost shows up later, as behaviour nobody asked for, as an upgrade
that turns something on, and as a debugging session that starts by finding out
which configuration produced a bean.

## Decision

Nothing in the library registers itself. There is no
`META-INF/spring/*.AutoConfiguration.imports`, and no `spring.factories`
outside the test-support module (below); adding a module to the classpath changes nothing until the
application asks for it, in one of these ways:

- an `@Import` or an annotation replacing spring's own (`@ApplicationProperties`,
  `@EnableCustomWebMvc` instead of `@EnableWebMvc`,
  `@EnableJpaWhitelistFilteringRepositories` instead of `@EnableJpaRepositories`);
- a spring security configurer applied with `http.with(...)`
  (`Principals.coalescing`, `HttpHeaderAuthentication.configurer`,
  `StrictContentSecurityPolicy.configurer`, `ClientErrors.configurer`);
- a builder (`UpstreamBuilder`, `RestExceptionResolver.builder()`,
  `ExceptionResolvers.configurer`, `EmailMessage.builder()`);
- a plain `WebMvcConfigurer` registration (`FilterRequestArgumentResolver`).

Spring Boot is used as a library, not as a framework:
`@EmbeddedTomcatWebMvcApplication` imports exactly the two auto-configurations
it needs (`DispatcherServletAutoConfiguration`,
`TomcatServletWebServerAutoConfiguration`) and declares its own beans, with no
`@EnableAutoConfiguration`.

## Consequences

- What an application runs is written in the application: reading its
  configuration classes is enough to know which filters, resolvers and
  converters are active.
- Setup is longer, and every module's readme opens with the lines that switch
  it on.
- An upgrade cannot activate a feature behind the application's back; it can
  only change the features the application already chose (and those changes
  follow 0002).
- The single exception is `data-jpa-test`, a test-support module: it registers
  a `ContextCustomizerFactory` through `spring.factories`, because that is the
  hook spring's test context offers, and it only acts on test classes that
  declare `@SharedContainer`.
