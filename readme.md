# optionfactory-spring

Opinionated Spring Framework extension libraries.

## Modules

| Module                                                                               | Description                                                        |
|--------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| [`applications-web-tomcat`](applications-web-tomcat/readme.md)                       | Embedded Tomcat + Spring MVC configuration.                        |
| [`authentication`](authentication/readme.md)                                         | Support for unifying Principals in Spring Security.                |
| [`authentication-authorization-code`](authentication-authorization-code/readme.md)   | OAuth2 authorization code flow components and OIDC logout.         |
| [`authentication-resource-server`](authentication-resource-server/readme.md)         | BearerTokenResolvers with JWT Header introspection.                |
| [`authentication-tokens`](authentication-tokens/readme.md)                           | Authentication via HTTP headers (JWS, opaque tokens, basic).       |
| [`client-reports`](client-reports/readme.md)                                         | Server-side logging for client-side errors.                        |
| [`content-security-policies`](content-security-policies/readme.md)                   | Nonce-based Strict Content Security Policy for Spring Security.    |
| [`context`](context/readme.md)                                                       | Property sources, direct field access, and devtools.               |
| [`context-web`](context-web/readme.md)                                               | Web-specific configurations for `context` and direct field access. |
| [`data-jpa`](data-jpa/readme.md)                                                     | Declarative whitelisted filters on JPA `@Entity` types.            |
| [`data-jpa-web`](data-jpa-web/readme.md)                                             | Spring MVC support for `data-jpa` filtering and pagination.        |
| [`downstream`](downstream/readme.md)                                                 | Annotations for client code generation.                            |
| [`downstream-maven-plugin`](downstream-maven-plugin/readme.md)                       | Maven plugin to generate Java DTOs or TypeScript types.            |
| [`email`](email/readme.md)                                                           | Email spooling, templating, and CSS inlining.                      |
| [`localized-enums`](localized-enums/readme.md)                                       | Declarative enum localization support.                             |
| [`marshaling-jackson`](marshaling-jackson/readme.md)                                 | Jackson modules and adapters for common types and quirks.          |
| [`marshaling-jaxb`](marshaling-jaxb/readme.md)                                       | JAXB `XmlAdapter`s for Temporals and Money.                        |
| [`optionfactory-spring-bom`](optionfactory-spring-bom/readme.md)                     | Project Bill of Materials.                                         |
| [`pdf`](pdf/readme.md)                                                               | PDF generation with Thymeleaf and openhtmltopdf.                   |
| [`pem`](pem/readme.md)                                                               | PEM-based `KeyStore`s and security providers.                      |
| [`problems`](problems/readme.md)                                                     | Problem data types and exceptions for RFC-7807 like reporting.     |
| [`problems-web`](problems-web/readme.md)                                             | REST exception resolver for reporting errors.                      |
| [`thymeleaf`](thymeleaf/readme.md)                                                   | `SingletonDialect` and `Money` dialect for Thymeleaf.              |
| [`upstream`](upstream/readme.md)                                                     | HTTP/SOAP clients based on Spring HTTP Interfaces.                 |
| [`upstream-alerts-email`](upstream-alerts-email/readme.md)                           | Email alerting for upstream client errors.                         |
| [`upstream-interceptor-jws`](upstream-interceptor-jws/readme.md)                     | JWS signing for upstream requests.                                 |
| [`upstream-interceptor-spring-oauth2`](upstream-interceptor-spring-oauth2/readme.md) | OAuth2 authentication for upstream clients.                        |
| [`validators`](validators/readme.md)                                                 | `jakarta.validation` based validators for common types.            |

## License

Simplified BSD License. See [license.md](license.md) for details.
