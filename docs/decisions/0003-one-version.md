# 0003. One version for every module, silent or costly breaks bump the major

Status: accepted

## Context

The modules are released from one reactor and depend on each other. Versioning
them independently would make every application solve a compatibility matrix,
and every cross-module change a coordinated release.

## Decision

Every module shares the reactor's version, in `MAJOR.MINOR` form (`28.2`).
`optionfactory-spring-bom` lists every module, so an application imports one
version and picks modules without stating it again. Releases go to maven
central, signed.

The major is bumped when a release contains a breaking change that is silent
(behaviour that changes without the build failing) or that costs users real
work to migrate. A break that the compiler points out and that is fixed
mechanically in place can ship in a minor. Changelog entries are tagged
(`[NEW]`, `[BREAKING]`, `[FIX]`, `[ENH]`, `[DOC]`, `[DEP]`, `[TEST]`) and
grouped per module, so a `[BREAKING]` entry in a minor is visible as such.

## Consequences

- Majors are frequent, and each one marks a batch of migrations described in
  the changelog.
- A module that did not change still gets a new version with every release;
  that is the price of not having a matrix.
- Applications that use several modules cannot mix versions, and never need
  to.
