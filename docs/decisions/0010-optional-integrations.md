# 0010. Integrations live in the integrating module, behind optional dependencies

Status: accepted

## Context

When two modules must understand each other, one of them has to depend on the
other. Choosing the wrong direction drags a web resolver into a persistence
module, or forces every application to carry modules it does not use.

## Decision

The module that integrates declares the integrated one as an `<optional>`
dependency and activates the integration only when it is present
(`ClassUtils.isPresent`); the integration classes live in their own package and
are loaded only then. problems-web answers `data-jpa`'s and `upstream`'s
exceptions this way, and neither of them depends on problems-web. The same
pattern makes devtools optional (`DevToolsImportSelector`) and keeps `pem`'s
spring integration optional.

It is not classpath scanning: only the modules this library ships are
registered like this, and third-party libraries contribute through
`ProblemsModule`s the application registers (0009).

## Consequences

- Modules stay usable alone; dependencies point from the module that knows
  more to the one that knows less.
- The integrating module owns the integration's tests, and must compile
  against the optional dependency.
- An integration activated by presence is an exception to 0001: having
  `data-jpa` on the classpath changes how problems-web answers its exceptions,
  which is the reason only the library's own modules are allowed to do it.
