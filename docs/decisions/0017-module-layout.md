# 0017. Modules are split by dependency footprint, named by family

Status: accepted

## Context

A single jar would impose servlet, jpa, xml and crypto dependencies on every
application; one module per class would make every application assemble a
puzzle.

## Decision

A module is split when the split keeps a dependency out of applications that
do not need it, and merged when it stops doing so:

- the core is separate from its web side (`problems` / `problems-web`,
  `data-jpa` / `data-jpa-web`, `context` / `context-web`), keeping servlet and
  spring mvc out of the core; the servlet api is always `provided`;
- families share a prefix (`authentication-*`, `upstream-*`,
  `upstream-interceptor-*`, `data-jpa-*`, `marshaling-*`, `applications-*`),
  and modules are renamed to fit them;
- modules are merged when the reason to split is gone (pdf signing went into
  `pdf` once it no longer needed bouncycastle).

## Consequences

- An application's dependency list says which features it uses.
- Cross-module integrations follow 0010.
- Renames and merges are breaking changes for build files, and are released
  as such (0003).
- Splitting is a trade-off with usability, not a rule applied mechanically:
  each split is one more dependency to know about and keep aligned. `upstream`
  keeps its SOAP and xml stack mandatory (0012) for that reason, at the cost
  of carrying it into REST-only applications.
