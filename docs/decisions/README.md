# Architecture Decision Records

Choices that constrain the whole library, or a whole family of modules, with
the reasoning behind each. They are recorded here because no single file is the
right place to explain them: rationale about one piece of code belongs in its
javadoc.

| # | Decision |
|---|----------|
| 0001 | [Opt-in by explicit configuration, no auto-configuration](0001-explicit-configuration.md) |
| 0002 | [Behaviour changes break the build, never the runtime](0002-behaviour-changes-break-the-build.md) |
| 0003 | [One version for every module, silent or costly breaks bump the major](0003-one-version.md) |
| 0004 | [Latest spring generation, built for the latest LTS jdk](0004-platform.md) |
| 0005 | [Secure by default, explicit opt-out for third-party reality](0005-secure-defaults.md) |
| 0006 | [Clients address names, never paths](0006-names-not-paths.md) |
| 0007 | [Configuration mistakes fail at startup, client mistakes are 400s](0007-fail-at-startup.md) |
| 0008 | [Errors are a list of typed problems](0008-problems.md) |
| 0009 | [Exception classification is open, specific before general](0009-open-classification.md) |
| 0010 | [Integrations live in the integrating module, behind optional dependencies](0010-optional-integrations.md) |
| 0011 | [Request parameters bind to fields directly](0011-field-binding.md) |
| 0012 | [Upstream clients are annotated http interfaces](0012-upstream.md) |
| 0013 | [Many authentication mechanisms, one principal](0013-one-principal.md) |
| 0014 | [Client code is generated from the server's compiled classes](0014-downstream.md) |
| 0015 | [Outbound email goes through a filesystem spool, at most once](0015-email-spool.md) |
| 0016 | [Integration tests run against the real database, committing per phase](0016-real-database-tests.md) |
| 0017 | [Modules are split by dependency footprint, named by family](0017-module-layout.md) |
| 0018 | [Crypto on the JCA, no bouncycastle at runtime](0018-jca-crypto.md) |
| 0019 | [slf4j api only, the backend is the application's](0019-logging.md) |

Status of all: accepted. Most decisions predate this
record, which writes down what the code, the readmes and the changelog (from
26.0 on) already say.
