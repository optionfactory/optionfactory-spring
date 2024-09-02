package net.optionfactory.spring.upstream.expressions;

import org.springframework.expression.EvaluationContext;

public interface IntExpression {

    public int evaluate(EvaluationContext context);

}
