package net.optionfactory.spring.email.l10n;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.l10n.LocalizationTest.Conf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(Conf.class)
public class LocalizationTest {

    public static class Conf {

        /// The message source can be a dedicated one, as here, or the application context itself,
        /// which then delegates to the `messageSource` bean, when one is defined.
        @Bean
        public EmailMessage.Prototype email(ConfigurableApplicationContext ac) {
            final var ms = new ResourceBundleMessageSource();
            ms.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
            ms.setBasenames("net/optionfactory/spring/email/l10n/test-l10n");
            ms.setUseCodeAsDefaultMessage(true);

            return EmailMessage.builder()
                    .sender("test.sender@example.com", "Test sender")
                    .recipient("test@example.com")
                    .subject("test subject")
                    .htmlBodyEngine(c -> c.html("/net/optionfactory/spring/email/l10n/", ms))
                    .htmlBodyTemplate("template.html")
                    .expressions(ac)
                    .prototype();
        }
    }

    @Autowired
    private EmailMessage.Prototype email;

    @Test
    public void canUseLocalizedMessages() {
        final var out = email
                .builder()
                .locale(Locale.ITALIAN)
                .marshal();
        final var output = new String(out, StandardCharsets.UTF_8);
        Assertions.assertTrue(output.contains("Content: italian"), "the message is resolved in the builder locale, got: " + output);
    }
}
