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

        @Bean
        public EmailMessage.Prototype email(ConfigurableApplicationContext ac) {
            //Here you can either create a specific messageSource, or pass the ac as the message source.
            //In the latter case if you defined a @Bean called messageSource(), that's what's being used.
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
        Assertions.assertTrue(output.contains("Content: italian"), output);
    }
}
