package net.optionfactory.spring.upstream.expressions;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;

public class BooleanExpression {

    private final Expression e;

    public BooleanExpression(Expression e) {
        this.e = e;
    }

    public boolean evaluate(EvaluationContext context) {
        return e.getValue(context, boolean.class);
    }

}
