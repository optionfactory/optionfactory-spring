package net.optionfactory.spring.thymeleaf;

import java.util.Set;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

/// Exposes an object to the templates as the expression object `#moduleName`, so that its public
/// methods can be called from any expression.
///
/// The same instance serves every template and every thread, so it must be thread-safe, typically
/// stateless.
///
/// ```java
/// engine.addDialect(SingletonDialect.of("money", new Money(new Money.ItalianSymbols())));
/// ```
///
/// ```html
/// <span th:text="${#money.formatCents(order.totalCents)}"></span>
/// ```
public class SingletonDialect implements IExpressionObjectDialect {

    private final Object functions;
    private final String moduleName;

    /// @param moduleName the name of the expression object, without the `#`, and of the dialect
    /// @param functions the object exposed to the templates
    public SingletonDialect(String moduleName, Object functions) {
        this.moduleName = moduleName;
        this.functions = functions;
    }

    /// @param moduleName the name of the expression object, without the `#`, and of the dialect
    /// @param functions the object exposed to the templates
    /// @return the dialect
    public static SingletonDialect of(String moduleName, Object functions) {
        return new SingletonDialect(moduleName, functions);
    }

    /// @return a factory exposing the object under the module name, and declaring it cacheable
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

    /// @return the module name
    @Override
    public String getName() {
        return moduleName;
    }
}
