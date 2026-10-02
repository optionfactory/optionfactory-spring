package net.optionfactory.spring.email;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EmailSenderConfigurationTest {

    @Test
    public void unsetOptionalSettingsGetTheirDefaults() {
        final var conf = EmailSenderConfiguration.builder()
                .host("smtp.example.com")
                .port(25)
                .protocol(EmailSenderConfiguration.Protocol.PLAIN)
                .build();

        Assertions.assertFalse(conf.placebo(), "emails are actually sent by default");
        Assertions.assertEquals(Duration.ofSeconds(30), conf.connectionTimeout(), "the connection timeout defaults to 30 seconds");
        Assertions.assertEquals(Duration.ofSeconds(60), conf.readTimeout(), "the read timeout defaults to 60 seconds");
        Assertions.assertEquals(Duration.ofSeconds(60), conf.writeTimeout(), "the write timeout defaults to 60 seconds");
        Assertions.assertTrue(conf.checkServerIdentity(), "the server identity is checked by default");
        Assertions.assertEquals(Optional.empty(), conf.username(), "no authentication by default");
        Assertions.assertEquals(Optional.empty(), conf.deadAfter(), "emails are retried forever by default");
        Assertions.assertEquals(Optional.empty(), conf.sslSocketFactory(), "the JDK socket factory is used by default");
    }

    @Test
    public void hostPortAndProtocolAreMandatory() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailSenderConfiguration.builder()
                .port(25).protocol(EmailSenderConfiguration.Protocol.PLAIN).build(), "the host is mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailSenderConfiguration.builder()
                .host("smtp.example.com").protocol(EmailSenderConfiguration.Protocol.PLAIN).build(), "the port is mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> EmailSenderConfiguration.builder()
                .host("smtp.example.com").port(25).build(), "the protocol is mandatory");
    }
}
