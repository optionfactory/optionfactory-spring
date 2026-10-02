# 0005. Secure by default, explicit opt-out for third-party reality

Status: accepted

## Context

A library's defaults end up in production: most applications never change a
setting they did not have to think about. At the same time the applications
integrate third parties that do not follow the standards a strict default
would enforce, and a library that cannot accept them gets forked or bypassed.

## Decision

Where the library owns the policy, the default is the safe one, and the unsafe
choice is a named, explicit call:

- JWTs are checked against a `ClaimsPolicy` that every configuration states
  (0002). The standard policies require `exp` and an issuer or audience;
  `ClaimsPolicy.permissive()` accepts third-party tokens without `exp`, `aud`
  or `iss`, and must stay available.
- `RestExceptionResolver` omits problem `details` unless told otherwise
  (`Details.OMIT`): details carry exception messages and internal state.
- `@Upstream.Logging` skips headers and abbreviates and redacts bodies by
  default.
- The strict content security policy is enforced, not report-only, by default.
- data-jpa filters are closed by default (0006).

Secrets and internals never reach clients or logs: an unauthenticated token's
principal is its header and scheme, never the credential; ambiguity warnings
name headers, never tokens; rejected filter requests say what was rejected
without revealing the entity behind it.

## Consequences

- An application that needs a looser setting has to write it, which leaves a
  line to grep for in a review.
- The content security policy is the known exception: `'unsafe-eval'` and the
  `'unsafe-inline' https:` fallbacks are on by default for compatibility with
  existing pages and older browsers. `'unsafe-eval'` stops being the default in
  the next major, as a compile-time break following 0002, so that pages relying
  on `eval` say so explicitly.
