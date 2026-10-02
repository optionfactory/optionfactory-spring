package net.optionfactory.spring.pem;

/// The unchecked exception reporting every failure to read PEM content: a parse error, an
/// unsupported label, an undecryptable key, or an `IOException` of the underlying source.
public class PemException extends RuntimeException {

    /// @param cause the failure being reported
    public PemException(Throwable cause) {
        super(cause);
    }

    /// @param reason the description of the failure
    public PemException(String reason) {
        super(reason);
    }

    /// Fails with a [PemException] unless `test` holds.
    ///
    /// @param test the condition that must hold
    /// @param message the [String#format(String, Object...)] pattern of the failure message
    /// @param values the arguments of the pattern
    /// @throws PemException when `test` is false
    public static void ensure(boolean test, String message, Object... values) {
        if (test) {
            return;
        }
        throw new PemException(String.format(message, values));
    }
}
