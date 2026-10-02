# 0002. Behaviour changes break the build, never the runtime

Status: accepted

## Context

A library change can make existing users behave differently while their code
still compiles: a stricter default, a check that starts rejecting what it used
to accept. Users upgrading find out from production failures, which is the most
expensive place to find out.

## Decision

A change that alters runtime behaviour for existing users must break them at
compile time: the old signature is removed, or the setting it defaulted
becomes a mandatory argument, so every caller has to make the choice
consciously. Where compilation cannot carry the break (an annotation
attribute), it moves to startup: the configuration is rejected when the
context is built, with a message naming the choice to make.

An explicit way to ask for the old behaviour is always kept when some users
legitimately need it.

Examples:

- 28.0: every `jws(...)`/`jwe(...)` configuration takes a `ClaimsPolicy` as its
  first argument, and the single-argument forms are gone.
  `ClaimsPolicy.permissive()` is the opt-out for third-party tokens that carry
  no `exp`, `aud` or `iss`, spelled out at the call site so it is never chosen
  by accident.
- 28.0: streaming `findAll` requires an explicit `SessionPolicy.Mode`.
- 28.0: `UnauthenticatedToken.getPrincipal()` returns a `HeaderAndScheme`, so
  code reading it as a `String` stops compiling instead of logging a token.
- 28.0: a collection-crossing filter declared with `Match.UNSTATED` is rejected
  at repository build rather than silently given a different meaning.

## Consequences

- Upgrades are noisier: they fail to compile, or to start, where a silent
  change would have passed. That noise is the point.
- Superseded API that can keep its old meaning is deprecated rather than
  removed (`DigestAuthClient.authenticate`, the `withUpstreamTransformer`
  no-ops), so only the users actually affected have to act.
- New limits that no legitimate caller reaches (the 28.2 bounds on filter
  request size) are not behaviour changes in this sense.
- Changes to logs only (levels, wording) are not covered.
- The changelog carries a before/after snippet for each break.
