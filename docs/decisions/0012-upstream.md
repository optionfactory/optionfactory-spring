# 0012. Upstream clients are annotated http interfaces

Status: accepted

## Context

Calls to external services need the same things every time: logging that does
not leak secrets, authentication, error mapping, alerting when a service
misbehaves, and a way to run without the service in development and demos.
Written by hand per client, each of those is solved slightly differently, and
some are forgotten.

## Decision

An upstream client is a spring http interface proxy over `RestClient`, built by
`UpstreamBuilder`, with HttpComponents 5 as the default transport. Its
behaviour is declared with the nested annotations of `@Upstream` (`Logging`,
`Mock`, `Header`, `Cookie`, `QueryParam`, `PathVariable`, `SoapAction`,
`ErrorOnResponse`, `AlertOnResponse`, `AlertOnRemotingError`, `Endpoint`,
`Principal`, `Context`, `HttpComponents`) on the interface or its methods.
Conditions and reasons are SpEL over the invocation, request, response and
exception. REST and SOAP clients go through the same builder.

- Authentication plugs in as initializers or interceptors (static tokens,
  oauth grants, digest, `upstream-interceptor-jws`,
  `upstream-interceptor-spring-oauth2`).
- Mocks ship with the client: `@Upstream.Mock` resources rendered by a mock
  request factory, switched on by the builder, so an application can run
  without the services it depends on.
- Alerts are spring application events (`UpstreamAlertEvent`); what to do with
  them is the application's choice, `upstream-alerts-email` being one.

## Consequences

- A client's behaviour is read from its interface, in one place.
- `upstream` carries the SOAP and xml stack (jaxb, saaj, jackson xml) as
  mandatory dependencies, even for REST-only applications.
- Logging defaults are safe (0005); redaction rules are declared per client.
