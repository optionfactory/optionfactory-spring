package net.optionfactory.spring.upstream.expressions;

import org.springframework.expression.EvaluationContext;

public interface StringExpression {

    public String evaluate(EvaluationContext context);

}
