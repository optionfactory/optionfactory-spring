package net.optionfactory.spring.upstream.expressions;

import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

public class ExpressionsTest {

    @Configuration
    @PropertySource(value = "classpath:test.properties", encoding = "UTF-8")
    public static class Config {

    }

    @Test
    public void canEvaluateEnvironmentProperty() {
        final var ac = new AnnotationConfigApplicationContext(Config.class);
        final var e = new Expressions(ac, Map.of());
        final var got = e.string("@environment.getProperty('test.value')", Expressions.Type.EXPRESSION).evaluate(e.context());
        Assert.assertEquals("my value", got);
    }

    @Test
    public void canAccessEnvironmentProperties() {
        final var ac = new AnnotationConfigApplicationContext(Config.class);
        final var e = new Expressions(ac, Map.of());
        final var got = e.string("@environment['test.value']", Expressions.Type.EXPRESSION).evaluate(e.context());
        Assert.assertEquals("my value", got);
    }

    @Test
    public void canAccessBoundVarialbe() {
        final var ac = new AnnotationConfigApplicationContext(Config.class);
        final var e = new Expressions(ac, Map.of("test", "my value"));
        final var got = e.string("#test", Expressions.Type.EXPRESSION).evaluate(e.context());
        Assert.assertEquals("my value", got);
    }
}
