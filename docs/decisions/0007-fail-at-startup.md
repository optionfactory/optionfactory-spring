# 0007. Configuration mistakes fail at startup, client mistakes are 400s

Status: accepted

## Context

A declaration mistake that surfaces on the first request using it reaches
production as a 500 on some rarely used screen. A client mistake answered as
a 500 hides the client's error in the server's logs and tells the client
nothing it can fix.

## Decision

Mistakes are told apart by whose they are, and reported where they can be
fixed:

- the application's: rejected when the context starts. data-jpa resolves every
  filter and sorter declaration when the repository is built and throws
  `InvalidFilterConfiguration` / `InvalidSortConfiguration`; a standard
  `ClaimsPolicy` cannot be built without an issuer or audience; enum
  localizations are scanned at construction.
- the client's: a dedicated exception (`InvalidFilterRequest`,
  `InvalidSortRequest`, every value parse failure normalized into them) that
  problems-web answers as a `400` with problems saying what was rejected.
- the server's: anything nobody classified, answered as a `500` with a single
  `SERVER_ERROR` problem and logged with its stack trace.

## Consequences

- Startup is slower and stricter; a context that starts has no latent
  configuration errors of these kinds.
- Client errors are logged at `DEBUG`, not as errors, so the logs show the
  server's failures only.
- A new failure mode must pick a side; "configuration or request" is part of
  reviewing it.
