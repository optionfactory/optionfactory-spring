package net.optionfactory.spring.pem;

public class PemException extends RuntimeException {

    public PemException(Throwable cause) {
        super(cause);
    }

    public PemException(String reason) {
        super(reason);
    }

    public static void ensure(boolean test, String message, Object... values) {
        if (test) {
            return;
        }
        throw new PemException(String.format(message, values));
    }
}
