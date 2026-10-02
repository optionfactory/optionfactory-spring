package net.optionfactory.spring.pem.der;

/// The unchecked exception reporting malformed or unexpected DER content.
public class DerException extends RuntimeException {

    /// @param cause the failure being reported
    public DerException(Throwable cause) {
        super(cause);
    }

    /// @param reason the description of the failure
    public DerException(String reason) {
        super(reason);
    }

    /// Fails with a [DerException] unless `test` holds.
    ///
    /// @param test the condition that must hold
    /// @param message the [String#format(String, Object...)] pattern of the failure message
    /// @param values the arguments of the pattern
    /// @throws DerException when `test` is false
    public static void ensure(boolean test, String message, Object... values) {
        if (test) {
            return;
        }
        throw new DerException(String.format(message, values));
    }
}
