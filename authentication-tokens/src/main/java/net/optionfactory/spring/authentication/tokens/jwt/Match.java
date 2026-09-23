package net.optionfactory.spring.authentication.tokens.jwt;

/// How a JWT processor treats a token found on its header, as decided by its matcher before anything
/// is verified. Processors sharing a header are consulted in configuration order.
public enum Match {
    /// Tries the token; if this processor rejects it, the next processor gets to try it. A token is
    /// rejected only when no processor accepts it.
    LAX,
    /// Claims the token: if this processor rejects it, the token is rejected and no later processor
    /// sees it. The default, and the right choice for a header with a single issuer.
    STRICT,
    /// Leaves the token to the next processor, unverified.
    SKIP;
}
