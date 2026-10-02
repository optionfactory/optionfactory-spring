package net.optionfactory.spring.upstream.expressions;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;

/// A string annotation attribute ready to be evaluated: a parsed SpEL expression or template, or a
/// static value. Thread-safe: one instance is evaluated by every invocation.
///
/// Instances are created by [Expressions#string].
public class StringExpression {

    private final Expression e;
    private final String value;

    /// @param e the parsed expression, or `null` for a static value
    /// @param value the static value, used only when `e` is `null`
    public StringExpression(Expression e, String value) {
        this.e = e;
        this.value = value;
    }

    /// @param context the variables and the beans the expression sees
    /// @return the static value, or the result of the expression converted to a string
    /// @throws org.springframework.expression.EvaluationException when the evaluation or the conversion
    /// fails
    public String evaluate(EvaluationContext context) {
        return e != null ? e.getValue(context, String.class) : value;
    }

}
