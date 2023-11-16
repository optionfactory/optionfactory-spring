package net.optionfactory.spring.email.connector;

import org.springframework.core.io.InputStreamSource;

public interface EmailConnector {

    public enum Protocol {
        PLAIN, TLS, START_TLS_SUPPORTED, START_TLS_REQUIRED;
    }

    void send(InputStreamSource emlSource);

    public static class EmailSendException extends IllegalStateException {

        public EmailSendException(String reason, Throwable cause) {
            super(reason, cause);
        }

    }

}
