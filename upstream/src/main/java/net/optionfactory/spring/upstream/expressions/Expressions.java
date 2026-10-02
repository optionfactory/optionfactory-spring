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

/// Parses the expressions of an upstream client and creates the contexts they are evaluated in.
///
/// Every context exposes the beans of the application context, if one is configured, as `@beanName`
/// (e.g. `@environment['some.property']`), and the variables configured with
/// [net.optionfactory.spring.upstream.UpstreamBuilder#var]. The contexts of an invocation add, as
/// variables:
///
/// - `#invocation` (the [InvocationContext]), `#upstream` and `#endpoint` (the names);
/// - `#args`, the arguments array, and each argument under its parameter name, hiding a configured
///   variable with the same name (parameter names require compiling with `-parameters`);
/// - `#request` ([RequestContext]), once a request exists;
/// - `#response` ([ResponseContext]), with the `#json_path(name)` function, returning the first field
///   of the json body with that name at any depth (a missing node when there is none, or when the
///   body is not buffered or not json), and the `#xpath_bool(xpath)` function, evaluating an xpath
///   over the xml body (false when the body is not buffered or not xml);
/// - `#exception` ([ExceptionContext]), for a failed exchange.
///
/// Expressions are parsed when the client is built, so a syntax error fails the build. One instance
/// serves every invocation of a client and is thread-safe; the contexts are not, and are created per
/// evaluation.
public class Expressions {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext templateContext = new TemplateParserContext();
    private final ConfigurableApplicationContext ac;
    private final Map<String, Object> vars;

    /// How a string annotation attribute is evaluated.
    public enum Type {
        /// Literal text with embedded `#{...}` expressions, e.g. `ok-#{#id}.json`.
        TEMPLATED,
        /// A SpEL expression as a whole, e.g. `#id + '.json'`.
        EXPRESSION,
        /// Literal text, never evaluated.
        STATIC;
    }

    /// @param ac the application context whose beans the expressions can reference, or `null`
    /// @param vars the variables visible to every expression, or `null` for none; not copied, so later
    /// changes to the map are seen by the contexts created afterwards
    public Expressions(@Nullable ConfigurableApplicationContext ac, @Nullable Map<String, Object> vars) {
        this.ac = ac;
        this.vars = vars == null ? Map.of() : vars;
    }

    /// @param value the attribute value
    /// @param type how the value is evaluated
    /// @return the parsed attribute
    /// @throws org.springframework.expression.ParseException when the value is not a valid expression or
    /// template
    public StringExpression string(String value, Type type) {
        final var tctx = type == Type.TEMPLATED ? templateContext : null;
        final var expr = type == Type.STATIC ? null : parser.parseExpression(value, tctx);
        final var svalue = type == Type.STATIC ? value : null;
        return new StringExpression(expr, svalue);
    }

    /// @param value a SpEL expression yielding a boolean
    /// @return the parsed condition
    /// @throws org.springframework.expression.ParseException when the value is not a valid expression
    public BooleanExpression bool(String value) {
        return new BooleanExpression(parser.parseExpression(value));
    }

    /// @param value a SpEL expression
    /// @return the parsed expression
    /// @throws org.springframework.expression.ParseException when the value is not a valid expression
    public Expression parse(String value) {
        return parser.parseExpression(value);
    }

    /// @param value literal text with embedded `#{...}` expressions
    /// @return the parsed template
    /// @throws org.springframework.expression.ParseException when the value is not a valid template
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

    /// @return a context with the beans and the configured variables only, as used for the values
    /// computed once when the client is built
    public OverlayEvaluationContext context() {
        final var ctx = new OverlayEvaluationContext(ac == null ? null : ac.getBeanFactory());
        ctx.setVariables(vars);
        return ctx;
    }

    private OverlayEvaluationContext baseContext(InvocationContext invocation) {
        final var ctx = context();
        ctx.setVariable("invocation", invocation);
        ctx.setVariable("upstream", invocation.endpoint().upstream());
        ctx.setVariable("endpoint", invocation.endpoint().name());
        return ctx;
    }

    /// @param invocation the invocation in progress
    /// @return a context with the invocation variables and the arguments
    public OverlayEvaluationContext context(InvocationContext invocation) {
        final var ctx = baseContext(invocation);
        bindArgs(ctx, invocation);
        return ctx;
    }

    /// @param invocation the invocation in progress
    /// @param request the request about to be sent
    /// @return a context with the invocation variables, the arguments and `#request`
    public OverlayEvaluationContext context(InvocationContext invocation, RequestContext request) {
        final var ctx = baseContext(invocation);
        ctx.setVariable("request", request);
        bindArgs(ctx, invocation);
        return ctx;
    }

    /// @param invocation the invocation in progress
    /// @param request the request sent
    /// @param response the response received
    /// @return a context with the invocation variables, the arguments, `#request`, `#response`,
    /// `#json_path` and `#xpath_bool`
    public OverlayEvaluationContext context(InvocationContext invocation, RequestContext request, ResponseContext response) {
        final var ctx = baseContext(invocation);
        ctx.setVariable("request", request);
        ctx.setVariable("response", response);
        ctx.setVariable("json_path", JsonPath.boundMethodHandle(invocation.converters(), response));
        ctx.setVariable("xpath_bool", XmlPath.xpathBooleanBoundMethodHandle(response));
        bindArgs(ctx, invocation);
        return ctx;
    }

    /// @param invocation the invocation in progress
    /// @param request the request that failed
    /// @param exception the failure
    /// @return a context with the invocation variables, the arguments, `#request` and `#exception`
    public OverlayEvaluationContext context(InvocationContext invocation, RequestContext request, ExceptionContext exception) {
        final var ctx = baseContext(invocation);
        ctx.setVariable("request", request);
        ctx.setVariable("exception", exception);
        bindArgs(ctx, invocation);
        return ctx;
    }

    /// Creates the context of the thymeleaf mock templates: `invocation`, `upstream`, `endpoint`,
    /// `args` and each argument under its parameter name, plus the beans of the application context, if
    /// any, through spring's thymeleaf evaluation context. The configured variables are not included.
    ///
    /// @param invocation the invocation in progress
    /// @return the thymeleaf context
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
