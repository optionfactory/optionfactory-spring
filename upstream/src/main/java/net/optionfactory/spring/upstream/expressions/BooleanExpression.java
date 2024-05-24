package net.optionfactory.spring.upstream.expressions;

import org.springframework.expression.EvaluationContext;

public interface BooleanExpression {

    public boolean evaluate(EvaluationContext context);

}
