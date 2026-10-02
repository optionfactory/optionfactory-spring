# 0018. Crypto on the JCA, no bouncycastle at runtime

Status: accepted

## Context

Bouncycastle is the usual answer to PEM files and CMS signatures in java, at
the cost of a large dependency, its own provider registration, and its release
cadence in every application.

## Decision

Cryptography uses the jdk's JCA only:

- `pem` parses PEM files with its own grammar and DER reader, and exposes them
  as a JCA `KeyStore` through its own provider.
- `pdf` builds PKCS#7 / CMS signatures with its own DER writer.
- JOSE (JWS, JWE) uses nimbus-jose-jwt.

Bouncycastle is a test dependency only, used to verify what the library
produces.

## Consequences

- No crypto provider to register, and no bouncycastle in applications.
- The library owns a small DER/PEM implementation, and its correctness: what
  it does not implement (e.g. EC private keys in PEM) is unsupported, not
  delegated.
