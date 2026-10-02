# 0009. Exception classification is open, specific before general

Status: accepted

## Context

Libraries throw exceptions that describe a bad request (an invalid filter, a
failed upstream call), and the resolver that answers them should not have to
know every library, nor every library depend on the resolver.

## Decision

`RestExceptionResolver` answers exceptions through `ExceptionClassifier`s,
bundled with transformers in `ProblemsModule`s. Classifiers are consulted
specific before general, like `catch` clauses: those the application registers
first, in registration order, then the built-in modules (spring mvc, bean
validation, `Failure`, spring security, spring's `ErrorResponse`s, and
`upstream` and `data-jpa` when present, see 0010). The first classifier that
does not decline answers; a classifier that throws is logged and skipped. A
module contributes cases but cannot reconfigure the resolver, and omitting
details always runs last.

The response kinds are resolved by a fixed chain installed by
`ExceptionResolvers.configurer`: undeliverable responses, rest handlers,
binary downloads, pages.

## Consequences

- A library makes its exceptions answerable by shipping a module; the
  application opts in by registering it (0001).
- An application classifier can refine a built-in case, and therefore must
  decline every exception it does not own.
- The order is part of the contract: a handler is answered by the first
  resolver in the chain that accepts it.
