package net.optionfactory.spring.upstream.expressions;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext.MessageConverters;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.http.converter.HttpMessageConverters;

public class ExpressionsTest {

    @Configuration
    @PropertySource(value = "classpath:test.properties", encoding = "UTF-8")
    public static class Config {

    }

    public interface Client {

        String call(String first, Integer second);
    }

    private static InvocationContext invocation(Expressions expressions, Object... args) throws NoSuchMethodException {
        final Method method = Client.class.getMethod("call", String.class, Integer.class);
        final var endpoint = new EndpointDescriptor("my-upstream", "my-endpoint", method, null);
        final var converters = new MessageConverters(HttpMessageConverters.forClient().build());
        return new InvocationContext(expressions, PayloadsRendering.builder().build(), converters, endpoint, args, "boot", 1, null, Buffering.BUFFERED);
    }

    @Test
    public void canEvaluateEnvironmentProperty() {
        final var ac = new AnnotationConfigApplicationContext(Config.class);
        final var e = new Expressions(ac, Map.of());
        final var got = e.string("@environment.getProperty('test.value')", Expressions.Type.EXPRESSION).evaluate(e.context());
        Assertions.assertEquals("my value", got, "@environment must resolve to the application context environment");
    }

    @Test
    public void canAccessEnvironmentProperties() {
        final var ac = new AnnotationConfigApplicationContext(Config.class);
        final var e = new Expressions(ac, Map.of());
        final var got = e.string("@environment['test.value']", Expressions.Type.EXPRESSION).evaluate(e.context());
        Assertions.assertEquals("my value", got, "environment properties must be indexable by name");
    }

    @Test
    public void canAccessBoundVarialbe() {
        final var ac = new AnnotationConfigApplicationContext(Config.class);
        final var e = new Expressions(ac, Map.of("test", "my value"));
        final var got = e.string("#test", Expressions.Type.EXPRESSION).evaluate(e.context());
        Assertions.assertEquals("my value", got, "configured variables must be bound in every context");
    }

    @Test
    public void staticValuesAreNeverEvaluated() {
        final var e = new Expressions(null, null);
        final var got = e.string("#{#missing} and #missing", Expressions.Type.STATIC).evaluate(e.context());
        Assertions.assertEquals("#{#missing} and #missing", got, "a STATIC value must be returned verbatim");
    }

    @Test
    public void templatedValuesEvaluateOnlyTheDelimitedParts() {
        final var e = new Expressions(null, Map.of("name", "world"));
        final var got = e.string("hello #{#name}", Expressions.Type.TEMPLATED).evaluate(e.context());
        Assertions.assertEquals("hello world", got, "a TEMPLATED value must evaluate #{...} and keep the surrounding text");
    }

    @Test
    public void expressionValuesAreEvaluatedAsAWhole() {
        final var e = new Expressions(null, Map.of("name", "world"));
        final var got = e.string("'hello ' + #name", Expressions.Type.EXPRESSION).evaluate(e.context());
        Assertions.assertEquals("hello world", got, "an EXPRESSION value must be evaluated as SpEL");
    }

    @Test
    public void beanReferencesFailWithoutAnApplicationContext() {
        final var e = new Expressions(null, null);
        final var expression = e.string("@environment", Expressions.Type.EXPRESSION);
        final var ctx = e.context();
        Assertions.assertThrows(SpelEvaluationException.class, () -> expression.evaluate(ctx), "bean references need an application context");
    }

    @Test
    public void invocationContextBindsArgumentsByNameAndPosition() throws NoSuchMethodException {
        final var e = new Expressions(null, null);
        final var ctx = e.context(invocation(e, "a", 2));
        Assertions.assertEquals("a", e.parse("#first").getValue(ctx), "arguments must be bound by parameter name");
        Assertions.assertEquals(2, e.parse("#args[1]").getValue(ctx), "arguments must be bound by position in #args");
        Assertions.assertEquals("my-upstream", e.parse("#upstream").getValue(ctx), "#upstream must be the upstream name");
        Assertions.assertEquals("my-endpoint", e.parse("#endpoint").getValue(ctx), "#endpoint must be the endpoint name");
        Assertions.assertEquals("boot", e.parse("#invocation.boot()").getValue(ctx), "#invocation must be the invocation context");
    }

    @Test
    public void argumentsShadowConfiguredVariablesWithTheSameName() throws NoSuchMethodException {
        final var e = new Expressions(null, Map.of("first", "configured", "other", "configured"));
        final var ctx = e.context(invocation(e, "argument", 2));
        Assertions.assertEquals("argument", e.parse("#first").getValue(ctx), "an argument must win over a configured variable with its name");
        Assertions.assertEquals("configured", e.parse("#other").getValue(ctx), "configured variables must still be visible");
    }

    @Test
    public void booleanExpressionsConvertTheirResult() {
        final var e = new Expressions(null, Map.of("flag", "true"));
        Assertions.assertTrue(e.bool("#flag").evaluate(e.context()), "a string result must be converted to a boolean");
        Assertions.assertFalse(e.bool("1 > 2").evaluate(e.context()), "a boolean result must be returned as is");
    }

    @Test
    public void variablesAreCopiedWhenConstructed() {
        final var vars = new HashMap<String, Object>();
        vars.put("a", "before");
        vars.put("nothing", null);
        final var expressions = new Expressions(null, vars);
        vars.put("a", "after");
        vars.put("b", "added");
        Assertions.assertEquals("before", expressions.parse("#a").getValue(expressions.context()), "changes to the map after construction must not be seen by the expressions");
        Assertions.assertNull(expressions.parse("#b").getValue(expressions.context()), "variables added to the map after construction must not be seen by the expressions");
    }
}
