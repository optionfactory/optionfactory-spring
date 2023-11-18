package net.optionfactory.spring.thymeleaf;

import java.util.Set;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

public class SingletonDialect implements IExpressionObjectDialect {

    private final Object functions;
    private final String moduleName;

    public SingletonDialect(String moduleName, Object functions) {
        this.moduleName = moduleName;
        this.functions = functions;
    }

    public static SingletonDialect of(String moduleName, Object functions) {
        return new SingletonDialect(moduleName, functions);
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return new IExpressionObjectFactory() {
            @Override
            public Set<String> getAllExpressionObjectNames() {
                return Set.of(moduleName);
            }

            @Override
            public Object buildObject(IExpressionContext context, String expressionObjectName) {
                return functions;
            }

            @Override
            public boolean isCacheable(String expressionObjectName) {
                return true;
            }
        };
    }

    @Override
    public String getName() {
        return moduleName;
    }
}
