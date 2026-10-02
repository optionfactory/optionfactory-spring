# 0004. Latest spring generation, built for the latest LTS jdk

Status: accepted

## Context

Supporting several spring generations or jdk lines means parallel branches,
or code limited to their common subset. Both slow down every change for the
benefit of applications that have not upgraded.

## Decision

The library targets the latest spring generation, and is built for clients on
the latest LTS jdk, today Java 25. The bytecode targets the previous LTS, Java
21 (`maven.compiler.release`), and Java 21 support is best effort: it compiles,
and nothing breaks it on purpose, but it is not what the library is tested and
tuned for. When a new LTS ships, clients get a grace period before the library
moves to it, and the LTS it leaves becomes the best effort one.

On the spring side that is Spring Framework 7, Spring Security 7, Spring Data
2026.0, Jakarta EE (Servlet 6.1, provided), Hibernate 7, Tomcat 11, Jackson 3
and JUnit 6; Boot 4 artifacts are managed for the embedded server only (0001).
A new spring generation is adopted in a major release shortly after it ships.

## Consequences

- Platform features up to the bytecode target are used directly: records,
  sealed types (`Result`), virtual threads (the embedded tomcat's executor),
  Jackson 3's `JsonMapper`.
- Applications must be on the same spring generation to upgrade the library;
  there is no maintenance branch for the previous one.
- Applications still on the previous LTS can upgrade the library, but a
  problem specific to that jdk is fixed only if it is cheap to.
- Workarounds for gaps in a dependency are temporary by design: the library's
  own Jackson 3 shims were removed once Jackson supported what they covered.
