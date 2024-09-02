package net.optionfactory.spring.upstream.expressions;

import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.paths.JsonPath;
import net.optionfactory.spring.upstream.paths.XmlPath;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.expression.BeanExpressionContextAccessor;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.lang.Nullable;

public class Expressions {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext templateContext = new TemplateParserContext();
    private final ConfigurableBeanFactory beanFactory;

    public enum Type {
        TEMPLATED, EXPRESSION, STATIC;
    }

    public static class StaticExpression implements StringExpression {

        final String value;

        public StaticExpression(String value) {
            this.value = value;
        }

        @Override
        public String evaluate(EvaluationContext context) {
            return value;
        }

    }

    public static class SpelStringExpression implements StringExpression {

        final Expression e;

        public SpelStringExpression(Expression e) {
            this.e = e;
        }

        @Override
        public String evaluate(EvaluationContext context) {
            return e.getValue(context, String.class);
        }

    }

    public static class SpelBooleanExpression implements BooleanExpression {

        final Expression e;

        public SpelBooleanExpression(Expression e) {
            this.e = e;
        }

        @Override
        public boolean evaluate(EvaluationContext context) {
            return e.getValue(context, boolean.class);
        }

    }

    public static class SpelIntExpression implements IntExpression {

        final Expression e;

        public SpelIntExpression(Expression e) {
            this.e = e;
        }

        @Override
        public int evaluate(EvaluationContext context) {
            return e.getValue(context, int.class);
        }

    }

    public Expressions(@Nullable ConfigurableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public StringExpression string(String value, Type type) {
        return switch (type) {
            case Type.TEMPLATED ->
                new SpelStringExpression(parser.parseExpression(value, templateContext));
            case Type.EXPRESSION ->
                new SpelStringExpression(parser.parseExpression(value));
            case Type.STATIC ->
                new StaticExpression(value);
        };
    }

    public BooleanExpression bool(String value) {
        return new SpelBooleanExpression(parser.parseExpression(value));
    }
    
    public IntExpression integer(String value) {
        return new SpelIntExpression(parser.parseExpression(value));
    }

    public StandardEvaluationContext context() {
        final var sec = new StandardEvaluationContext(beanFactory == null ? null : new BeanExpressionContext(beanFactory, null));
        sec.addPropertyAccessor(new BeanExpressionContextAccessor());
        sec.addPropertyAccessor(new BeanFactoryAccessor());
        sec.addPropertyAccessor(new MapAccessor());
        sec.addPropertyAccessor(new EnvironmentAccessor());
        if (beanFactory != null) {
            sec.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }
        sec.setTypeLocator(new StandardTypeLocator(beanFactory != null ? beanFactory.getBeanClassLoader() : null));
        sec.setTypeConverter(new StandardTypeConverter(() -> {
            ConversionService cs = beanFactory != null ? beanFactory.getConversionService() : null;
            return (cs != null ? cs : DefaultConversionService.getSharedInstance());
        }));
        return sec;
    }

    public EvaluationContext context(InvocationContext invocation, RequestContext request) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("request", request);
        final var params = invocation.endpoint().method().getParameters();
        final var args = invocation.arguments();
        ctx.setVariable("args", args);
        for (int i = 0; i != params.length; ++i) {
            ctx.setVariable(params[i].getName(), args[i]);
        }
        return ctx;
    }

    public EvaluationContext context(InvocationContext invocation, RequestContext request, ResponseContext response) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("request", request);
        ctx.setVariable("response", response);

        final var params = invocation.endpoint().method().getParameters();
        final var args = invocation.arguments();
        ctx.setVariable("args", args);
        for (int i = 0; i != params.length; ++i) {
            ctx.setVariable(params[i].getName(), args[i]);
        }
        ctx.registerFunction("json_path", JsonPath.boundMethodHandle(invocation.converters(), response));
        ctx.registerFunction("xpath_bool", XmlPath.xpathBooleanBoundMethodHandle(response));
        return ctx;
    }

    public EvaluationContext context(InvocationContext invocation, RequestContext request, ExceptionContext exception) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("request", request);
        ctx.setVariable("exception", exception);
        final var params = invocation.endpoint().method().getParameters();
        final var args = invocation.arguments();
        ctx.setVariable("args", args);
        for (int i = 0; i != params.length; ++i) {
            ctx.setVariable(params[i].getName(), args[i]);
        }
        return ctx;
    }

    public EvaluationContext context(InvocationContext invocation) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("upstream", invocation.endpoint().upstream());
        ctx.setVariable("endpoint", invocation.endpoint().name());
        final var params = invocation.endpoint().method().getParameters();
        final var args = invocation.arguments();
        ctx.setVariable("args", args);
        for (int i = 0; i != params.length; ++i) {
            ctx.setVariable(params[i].getName(), args[i]);
        }
        return ctx;
    }
}
