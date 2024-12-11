package net.optionfactory.spring.email;

import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.email.CanReferenceBeansInExpressionsTest.Conf;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = Conf.class)
public class CanReferenceBeansInExpressionsTest {

    public static class Conf {

        public record Info(String help) {

        }

        @Bean
        public Info info() {
            return new Info("Help Message");
        }

        @Bean
        public TemplateEngine emailTemplateEngine() {
            final var resolver = new StringTemplateResolver();
            resolver.setOrder(1);
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCacheable(true);
            final var engine = new SpringTemplateEngine();
            engine.addTemplateResolver(resolver);
            return engine;
        }

        @Bean
        public EmailMessage.Prototype email(ConfigurableApplicationContext ac, TemplateEngine emailTemplateEngine) {
            return EmailMessage.builder()
                    .sender("test.sender@example.com", "Test sender")
                    .recipient("test@example.com")
                    .subject("test subject")
                    .htmlBodyEngine(emailTemplateEngine)
                    .htmlBodyTemplate("[[${@info.help()}]]")
                    .applicationContext(ac)
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

        Assert.assertTrue(new String(out, StandardCharsets.UTF_8).contains("Help Message"));
    }
}
