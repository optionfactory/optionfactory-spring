package net.optionfactory.spring.pem.der;

public class DerException extends RuntimeException {

    public DerException(Throwable cause) {
        super(cause);
    }

    public DerException(String reason) {
        super(reason);
    }

    public static void ensure(boolean test, String message, Object... values) {
        if (test) {
            return;
        }
        throw new DerException(String.format(message, values));
    }
}
