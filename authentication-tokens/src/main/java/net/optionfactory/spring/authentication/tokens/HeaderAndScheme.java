package net.optionfactory.spring.authentication.tokens;

import java.util.Locale;

/// A request header and the auth-scheme its value is expected to be prefixed with, as the matcher
/// needs it: upper-cased, and carrying the separating space that follows a scheme in
/// `Authorization: Bearer xyz`. A blank scheme means the header carries the bare token instead, so
/// no separator is appended -- appending one would make the matcher look for a leading space that
/// such a header never has.
///
/// Normalising in the constructor keeps every construction path correct, including direct ones.
///
/// @param header the request header to read
/// @param scheme the auth-scheme its value is prefixed with, or blank for a bare token
public record HeaderAndScheme(String header, String scheme) {

    public HeaderAndScheme {
        final var normalized = scheme.toUpperCase(Locale.ROOT).trim();
        scheme = normalized.isEmpty() ? "" : normalized + " ";
    }

    /// For a header carrying the bare token, with no auth-scheme prefix.
    ///
    /// @param header the request header to read
    /// @return the pair matching that header's whole value
    public static HeaderAndScheme schemeless(String header) {
        return new HeaderAndScheme(header, "");
    }

}
