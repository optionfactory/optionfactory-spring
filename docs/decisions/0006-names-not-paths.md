# 0006. Clients address names, never paths

Status: accepted

## Context

Most filtering layers let a client send a path (`firstName==john`,
`?owner.city=roma`), which makes the entity graph the query surface. The path
becomes the contract, so refactoring the entity breaks clients; and securing it
means enumerating what must not be reachable, which is unbounded: in a
multi-tenant schema `owner.organization.…` is one hop from another tenant's
rows.

## Decision

data-jpa filters and sorters are a closed whitelist declared on the `@Entity`.
Each one binds a client-facing name to a server-side path (`@TextCompare`,
`@NumberCompare`, `@InstantCompare`, `@LocalDateCompare`, `@BooleanCompare`,
`@InEnum`, `@InList`, `@TextSearch`, `@Filterable`, `@Sortable`). A request
carries names only; a name that is not declared is rejected. A `FilterRequest`
is a conjunction: no `OR`, no nesting, each filter at most once. Sorts, server
built ones included, use `@Sortable` names. Everything is resolved against the
JPA metamodel when the repository is built (0007).

Server-side expressiveness is not limited: a base `Specification` applies any
condition to every query (tenant scoping, soft deletes), and `@Filterable`
binds a custom filter that builds anything the criteria API can, behind a name.

## Consequences

- Entity refactorings (renaming a property, moving it into an embeddable,
  turning a `@ManyToOne` into a collection) do not change the contract.
- Clients cannot compose ad-hoc boolean queries. Applications that need that
  want a query language (RSQL, spring-filter) and the coupling it brings; this
  module is not for them.
- Derived query methods bypass the sort whitelist and must not receive client
  supplied sorts.
- Request size is bounded, so a closed contract cannot be abused by volume.
