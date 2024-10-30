# Opinionated Spring Framework extension libraries

## [`marshaling-jaxb`](marshaling-jaxb/readme.md)

JAXB `XmlAdapter`s for Temporals and Money.

## [`marshaling-jackson`](marshaling-jackson/readme.md)

Jackson modules and adapters. Notably, the Quirks module handles per-`ObjectMapper` serialization/deseralization quirks.

## [`data-jpa`](data-jpa/readme.md), [`data-jpa-web`](data-jpa-web/readme.md)

Declarative whitelisted filters on `@Entity` types.

## [`context`](context/readme.md), [`context-web`](context-web/readme.md)

Property source configuration, conditional beans and WebMvc direct field access configuration.

## [`problems`](problems/readme.md), [`problems-web`](problems-web/readme.md)

REST exception resolver for reporting errors in API responses (e.g.: validation).

## [`feedback-web`](feedback-web/readme.md)

Server side client errors logging.

## [`authentication-bearer-token`](authentication-bearer-token/readme.md)

Bearer token and static bearer token `AuthenticationProvider`s for Spring Security.

## [`authentication-jws`](authentication-jws/readme.md)

JWS token `AuthenticationProvider` for Spring Security using `io.jsonwebtoken:jjwt`

## [`oidc-authorization-code`](oidc-authorization-code/readme.md)

## [`content-security-policies`](content-security-policies/readme.md)

Nonce Based [Strict Content Security Policy](https://web.dev/articles/strict-csp) for Spring Security.

## [`email`](email/readme.md)

Email spooling, templating and inlining.

## [`upstream`](upstream/readme.md)

[`HTTP Interface`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface) 
/ [`RestClient`](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html) 
/ [`HttpComponents 5`](https://hc.apache.org/httpcomponents-client-5.4.x/migration-guide/index.html) 
SOAP and REST clients with support for:

* Context Aware Logging
* Context Aware Interception
* Alerting
* Monitoring
* Error handling and mapping
* SpEL+annotations based configuration
* Mocking


## [`upstream-interceptor-jjwt`](upstream-interceptor-jjwt/readme.md)

A `io.jsonwebtoken:jjwt` based JWS/JWT interceptor for upstream clients.

## [`upstream-interceptor-spring-oauth2`](upstream-interceptor-spring-oauth2/readme.md)

An [`OAuth2AuthorizedClientManager`](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/client/OAuth2AuthorizedClientManager.html) based interceptor for upstream clients.

## [`upstream-email-faults`](upstream-email-faults/readme.md)

An upstream interceptor buffering and sending emails when alerts are generated.

## [`validators`](validators/readme.md)

`jakarta.validation` based validators for emails, `MultipartFile`s, IBANs, phone numbers, and tax codes.

## [`thymeleaf`](thymeleaf/readme.md)


## [`localized-enums`](localized-enums/readme.md)


## [`pdf`](pdf/readme.md)

Simplified PDF generations with [Thymeleaf]() + [openhtmltopdf](https://github.com/openhtmltopdf/openhtmltopdf) + [pdfbox](https://pdfbox.apache.org/)


## [`pdf-signing`](pdf-signing/readme.md)

FILTER_ADOBE_PPKLITE/SUBFILTER_ADBE_PKCS7_DETACHED PDF signing using [`bouncycastle`](https://www.bouncycastle.org/) and [pdfbox](https://pdfbox.apache.org/)


## [`pem`](pem/readme.md)

PEM based `Keystore`s 

## [`optionfactory-spring-bom`](optionfactory-spring-bom/readme.md)

Project bill of materials.
