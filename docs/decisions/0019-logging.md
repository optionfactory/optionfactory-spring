# 0019. slf4j api only, the backend is the application's

Status: accepted

## Context

A library that picks a logging backend forces it on applications, and two
libraries picking different ones force bridges on them.

## Decision

Main code logs through the slf4j api only. Backends are test scope
(`log4j-slf4j2-impl`, with a `log4j2-test.properties` per module).
commons-logging is excluded in favour of spring's own bridge. The exception is
`applications-web-tomcat`, which starts applications and therefore ships a
backend.

Log levels are part of the contract: client errors are logged at `DEBUG`,
failures of the server at `WARN` or `ERROR`, and silent fallbacks (an unknown
redaction, a missing custom locale resolver) at `WARN`.

## Consequences

- Applications choose and configure their backend.
- Changing the level a condition is logged at is a visible change, noted in the
  changelog even when it is not a breaking one.
