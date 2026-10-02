# 0008. Errors are a list of typed problems

Status: accepted

## Context

An API error response has to tell the client everything that went wrong, the
common case being several invalid fields at once, each with its own path and
message. RFC 7807 / 9457 (`application/problem+json`, spring's `ProblemDetail`)
standardizes a single object with a type, title, status and detail, and says
nothing about how to report several field errors: every application invents
its own extension member for that, so adopting it is ceremony without a
contract.

## Decision

An error is a list of `Problem`s, served as a json array with content type
`application/failures+json`:

```json
[
  {"type": "FIELD_ERROR", "context": "address.zip", "reason": "must not be blank", "details": null},
  {"type": "OBJECT_ERROR", "context": null, "reason": "dates are reversed", "details": null}
]
```

- `type` classifies the problem (`FIELD_ERROR`, `OBJECT_ERROR`,
  `REQUEST_ERROR`, `SERVER_ERROR`, `UPSTREAM_ERROR`, `FORBIDDEN`, or an
  application value);
- `context` locates it, a field path as the client sent it;
- `reason` is localized through the message source, for the client to show;
- `details` is for debugging, and omitted in production (0005).

The http status travels in the response, not in the body. Applications throw a
`Failure` carrying problems, or return a `Result` (a sealed `Ok`/`Err`), from
the `problems` module, which has no web dependency.

## Consequences

- One shape for every error: a single validation failure, many of them, an
  upstream failure, a server error. Clients write one error handler.
- The format is not a standard any generic client knows; it is documented by
  the `problems` module, and clients are expected to know it.
- Field paths are the names the client sent, not java property paths, so they
  can be matched to the inputs that produced them.
