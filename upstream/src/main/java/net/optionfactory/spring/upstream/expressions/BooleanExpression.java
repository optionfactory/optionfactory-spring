package net.optionfactory.spring.upstream.expressions;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;

/// A parsed SpEL condition, such as the ones of `@Upstream.AlertOnResponse` or of the `condition`
/// attributes. Thread-safe: one instance is evaluated by every invocation.
public class BooleanExpression {

    private final Expression e;

    /// @param e the parsed expression
    public BooleanExpression(Expression e) {
        this.e = e;
    }

    /// @param context the variables and the beans the expression sees
    /// @return the result, converted to a boolean when the expression yields another type (e.g. the
    /// string `"true"`)
    /// @throws org.springframework.expression.EvaluationException when the evaluation or the conversion
    /// fails
    public boolean evaluate(EvaluationContext context) {
        return e.getValue(context, boolean.class);
    }

}
