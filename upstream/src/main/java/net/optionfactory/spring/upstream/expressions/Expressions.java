package net.optionfactory.spring.upstream.expressions;

import java.util.Map;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.paths.JsonPath;
import net.optionfactory.spring.upstream.paths.XmlPath;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;

public class Expressions {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext templateContext = new TemplateParserContext();
    private final ConfigurableApplicationContext ac;
    private final Map<String, Object> vars;

    public enum Type {
        TEMPLATED, EXPRESSION, STATIC;
    }

    public Expressions(@Nullable ConfigurableApplicationContext ac, @Nullable Map<String, Object> vars) {
        this.ac = ac;
        this.vars = vars == null ? Map.of() : vars;
    }

    public StringExpression string(String value, Type type) {
        final var tctx = type == Type.TEMPLATED ? templateContext : null;
        final var expr = type == Type.STATIC ? null : parser.parseExpression(value, tctx);
        final var svalue = type == Type.STATIC ? value : null;
        return new StringExpression(expr, svalue);
    }

    public BooleanExpression bool(String value) {
        return new BooleanExpression(parser.parseExpression(value));
    }

    public Expression parse(String value) {
        return parser.parseExpression(value);
    }

    public Expression parseTemplated(String value) {
        return parser.parseExpression(value, templateContext);
    }

    private static void bindArgs(EvaluationContext ctx, InvocationContext invocation) {
        final var params = invocation.endpoint().method().getParameters();
        final var args = invocation.arguments();
        ctx.setVariable("args", args);
        for (int i = 0; i != params.length; ++i) {
            ctx.setVariable(params[i].getName(), args[i]);
        }
    }

    public OverlayEvaluationContext context() {
        final var ctx = new OverlayEvaluationContext(ac == null ? null : ac.getBeanFactory());
        ctx.setVariables(vars);
        return ctx;
    }

    public OverlayEvaluationContext context(InvocationContext invocation) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("upstream", invocation.endpoint().upstream());
        ctx.setVariable("endpoint", invocation.endpoint().name());
        bindArgs(ctx, invocation);
        return ctx;
    }

    public OverlayEvaluationContext context(InvocationContext invocation, RequestContext request) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("upstream", invocation.endpoint().upstream());
        ctx.setVariable("endpoint", invocation.endpoint().name());
        ctx.setVariable("request", request);

        bindArgs(ctx, invocation);
        return ctx;
    }

    public OverlayEvaluationContext context(InvocationContext invocation, RequestContext request, ResponseContext response) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("upstream", invocation.endpoint().upstream());
        ctx.setVariable("endpoint", invocation.endpoint().name());
        ctx.setVariable("request", request);
        ctx.setVariable("response", response);
        ctx.setVariable("json_path", JsonPath.boundMethodHandle(invocation.converters(), response));
        ctx.setVariable("xpath_bool", XmlPath.xpathBooleanBoundMethodHandle(response));
        bindArgs(ctx, invocation);
        return ctx;
    }

    public OverlayEvaluationContext context(InvocationContext invocation, RequestContext request, ExceptionContext exception) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("upstream", invocation.endpoint().upstream());
        ctx.setVariable("endpoint", invocation.endpoint().name());
        ctx.setVariable("request", request);
        ctx.setVariable("exception", exception);
        bindArgs(ctx, invocation);
        return ctx;
    }

    public Context thymeleafContext(InvocationContext invocation) {
        final var ctx = new Context();
        if (ac != null) {
            ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME, new ThymeleafEvaluationContext(ac, ac.getBeanFactory().getConversionService()));
        }
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
