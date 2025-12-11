package net.optionfactory.spring.email;

import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.email.CanReferenceBeansInExpressionsTest.Conf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.thymeleaf.templatemode.TemplateMode;

@SpringJUnitConfig(Conf.class)
public class CanReferenceBeansInExpressionsTest {

    public static class Conf {

        public record Info(String help) {

        }

        @Bean
        public Info info() {
            return new Info("Help Message");
        }

        @Bean
        public EmailMessage.Prototype email(ConfigurableApplicationContext ac) {
            return EmailMessage.builder()
                    .sender("test.sender@example.com", "Test sender")
                    .recipient("test@example.com")
                    .subject("test subject")
                    .htmlBodyEngine(c -> c.string(TemplateMode.HTML))
                    .htmlBodyTemplate("""
                        [[${@info.help()}]]
                    """)
                    .expressions(ac)
                    .prototype();
        }
    }

    @Autowired
    private EmailMessage.Prototype email;

    @Test
    public void canReferenceApplicationContextBeansFromTheThymeleafTemplate() {
        final var out = email
                .builder()
                .marshal();

        Assertions.assertTrue(new String(out, StandardCharsets.UTF_8).contains("Help Message"));
    }
}
