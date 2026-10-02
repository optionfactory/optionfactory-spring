# 0013. Many authentication mechanisms, one principal

Status: accepted

## Context

Applications accept several kinds of credentials at once: an oauth2 login,
locally signed tokens, static integration tokens, third-party JWTs, basic
auth. Spring security gives each mechanism its own principal type, and makes
mechanisms sharing the `Authorization` header contend for it.

## Decision

- `authentication-tokens` handles credentials carried by http headers (opaque
  tokens, basic, JWS, JWE) through token processors. They run in
  configuration order, except that every JWS and JWE configuration is gathered
  into one processor that runs last. A lax static token (`bearer`, `token`,
  `basic`) lets any other token on its header through to the next processor; a
  strict one (`bearerStrict`, `tokenStrict`) owns its header and scheme and
  rejects every other token found there, so it cannot share that header with
  static tokens configured after it, nor with JWTs.
- A rejected token contributes no authentication and invalidates nothing:
  mechanisms earlier in the chain keep what they established, and later
  filters (spring's resource server among them) still get to process the
  header. Two tokens on one request leave it unauthenticated rather than
  failing it.
- `authentication-resource-server` lets spring's oauth2 resource server share
  the bearer header, by declining the tokens it does not own
  (`JwtTokenResolverAdapter`).
- `authentication` coalesces whatever mechanism authenticated the request into
  one application principal type (`Principals.coalescing`); anonymous requests
  are left alone, and an authenticated principal no strategy maps is an error.

## Consequences

- Application code depends on one principal type, whatever authenticated the
  request.
- Adding a mechanism is configuration, not a change to the application's
  security code.
- Which mechanism owns a token must be decidable from the token itself (its
  header, scheme, jws header), before any verification.
- Sharing a header is a configuration decision: lax static tokens and JWTs can
  share one, a strict static token cannot share its own, and a configuration
  that tries fails when the security chain is built (0007).
