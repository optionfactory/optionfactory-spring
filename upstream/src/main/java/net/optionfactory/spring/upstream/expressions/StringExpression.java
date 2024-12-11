package net.optionfactory.spring.upstream.expressions;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;

public class StringExpression {

    private final Expression e;
    private final String value;

    public StringExpression(Expression e, String value) {
        this.e = e;
        this.value = value;
    }

    public String evaluate(EvaluationContext context) {
        return e != null ? e.getValue(context, String.class) : value;
    }

}
