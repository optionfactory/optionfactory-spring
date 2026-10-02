# 0014. Client code is generated from the server's compiled classes

Status: accepted

## Context

Clients of the applications' APIs (java services, typescript frontends) need
the server's DTOs. Generating them through an OpenAPI document adds an
intermediate model that loses what the java types say (records, nullability,
nested types, enums as unions), and generators produce code that has to be
fixed by hand or configured around, rather than the types the client
expected.

## Decision

Downstream is for clients of our own APIs, where we own both sides; it is not
meant to generate clients from external contracts. The server's compiled
classes are the source. `downstream` is a
dependency-free set of annotations (`@Downstream.Method(clients = ...)`,
`@Downstream.Ignore`, `@Downstream.Rename`) that marks which endpoints belong
to which client. `downstream-maven-plugin` takes the server artifact as a plugin
dependency, finds the marked endpoints by reflection, walks the payload types
they reference, and emits java DTOs (records by default) or typescript
declarations. Nullability follows jspecify `@Nullable`; type translations
(e.g. `MultipartFile`) are configured per execution.

## Consequences

- The generated types mirror the java ones, with no intermediate schema to
  keep faithful.
- Generation needs the server built first, and only covers what reflection can
  see: payload types, not the http contract (paths, verbs, status codes).
- The server has a compile dependency on `downstream`, which carries
  annotations only.
- There is no published contract for third parties: an API meant for external
  consumers documents itself by other means.
