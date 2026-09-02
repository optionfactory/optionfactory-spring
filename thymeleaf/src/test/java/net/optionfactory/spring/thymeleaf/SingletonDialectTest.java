package net.optionfactory.spring.thymeleaf;

import java.util.Locale;
import java.util.Set;
import net.optionfactory.spring.thymeleaf.dialects.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

public class SingletonDialectTest {

    @Test
    public void expressionObjectIsAvailableInTemplatesUnderTheConfiguredName() {
        final var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        engine.addDialect(SingletonDialect.of("money", new Money(new Money.ItalianSymbols())));

        final var context = new Context(Locale.ITALIAN);
        context.setVariable("cents", 123456L);

        final var rendered = engine.process("[[${#money.formatCents(cents)}]]", context);

        Assertions.assertEquals("1.234,56", rendered);
    }

    @Test
    public void sameInstanceIsReturnedForEveryEvaluation() {
        final var functions = new Money(new Money.ItalianSymbols());
        final var dialect = SingletonDialect.of("money", functions);

        Assertions.assertEquals("money", dialect.getName());
        final var factory = dialect.getExpressionObjectFactory();
        Assertions.assertEquals(Set.of("money"), factory.getAllExpressionObjectNames());
        Assertions.assertSame(functions, factory.buildObject(null, "money"));
        Assertions.assertTrue(factory.isCacheable("money"));
    }
}
