package net.optionfactory.spring.upstream.expressions;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.expression.BeanExpressionContextAccessor;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ConstructorResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.IndexAccessor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.ReflectiveConstructorResolver;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardOperatorOverloader;
import org.springframework.expression.spel.support.StandardTypeComparator;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;

/// A SpEL evaluation context whose variables are stacked in overlays: a derived context sees the
/// variables of the one it derives from, and can hide them, without copying or changing them.
///
/// The overlays share the maps of the contexts they derive from, so a variable set on a context
/// afterwards is visible through its overlays too. Variables cannot be assigned by expressions.
///
/// Like spring's `StandardEvaluationContext`, it resolves types, constructors, methods and
/// properties reflectively; given a bean factory, it adds the bean references (`@name`) and the
/// access to beans and environment properties as properties. It is not a sandbox: type
/// references, constructors and methods are all available.
///
/// Not thread-safe; create one per evaluation.
public class OverlayEvaluationContext implements EvaluationContext {

    private static final List<PropertyAccessor> PROPERTY_ACCESSORS = List.of(
            new BeanExpressionContextAccessor(),
            new BeanFactoryAccessor(),
            new MapAccessor(),
            new EnvironmentAccessor(),
            new ReflectivePropertyAccessor()
    );
    private static final List<IndexAccessor> INDEX_ACCESSORS = List.of();
    private static final List<ConstructorResolver> CTOR_RESOLVERS = List.of(
            new ReflectiveConstructorResolver()
    );
    private static final List<MethodResolver> METHOD_RESOLVERS = List.of(
            new ReflectiveMethodResolver()
    );
    private static final TypeComparator TYPE_COMPARATOR = new StandardTypeComparator();
    private static final OperatorOverloader OPERATOR_OVERLOADER = new StandardOperatorOverloader();

    private final TypedValue rootObject;
    private final BeanResolver beanResolver;
    private final TypeLocator typeLocator;
    private final TypeConverter typeConverter;

    private final ArrayDeque<Map<String, Object>> variables;

    /// Creates a root context.
    ///
    /// @param beanFactory resolves the bean references and provides the conversion service and the
    /// class loader, or `null` for a context without beans
    public OverlayEvaluationContext(ConfigurableBeanFactory beanFactory) {
        this.rootObject = new TypedValue(beanFactory == null ? null : new BeanExpressionContext(beanFactory, null));
        this.beanResolver = beanFactory != null ? new BeanFactoryResolver(beanFactory) : null;
        this.typeLocator = new StandardTypeLocator(beanFactory != null ? beanFactory.getBeanClassLoader() : null);
        this.typeConverter = new StandardTypeConverter(() -> {
            ConversionService cs = beanFactory != null ? beanFactory.getConversionService() : null;
            return (cs != null ? cs : DefaultConversionService.getSharedInstance());
        });

        this.variables = new ArrayDeque<>();
        this.variables.add(new HashMap<>());
    }

    /// Creates an overlay of another context, with an empty top overlay.
    ///
    /// @param other the context to derive from
    public OverlayEvaluationContext(OverlayEvaluationContext other) {
        this.rootObject = other.rootObject;
        this.beanResolver = other.beanResolver;
        this.typeLocator = other.typeLocator;
        this.typeConverter = other.typeConverter;

        this.variables = new ArrayDeque<>();
        this.variables.addAll(other.variables);
        this.variables.add(new HashMap<>());
    }

    /// Sets a variable in the top overlay.
    ///
    /// @param name the name, ignored when `null`
    /// @param value the value; `null` removes the variable from the top overlay only, so a variable of
    /// the same name in an underlying overlay becomes visible again
    @Override
    public void setVariable(String name, Object value) {
        if (name != null) {
            final var latestOverlay = this.variables.getLast();
            if (value != null) {
                latestOverlay.put(name, value);
            } else {
                latestOverlay.remove(name);
            }
        }
    }

    /// Sets several variables in the top overlay.
    ///
    /// @param values the variables; a `null` value is looked up as an absent variable
    public void setVariables(Map<String, Object> values) {
        this.variables.getLast().putAll(values);
    }

    /// @param name the variable name
    /// @return the value of the topmost overlay defining the variable, or `null` when none does
    @Override
    public Object lookupVariable(String name) {
        for (final var overlay : variables.reversed()) {
            final var value = overlay.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /// @return false: expressions cannot assign variables
    @Override
    public boolean isAssignmentEnabled() {
        return false;
    }

    /// @return a new context deriving from this one
    public OverlayEvaluationContext createOverlay() {
        return new OverlayEvaluationContext(this);
    }

    /// @param name the name of a variable to set in the new overlay
    /// @param value its value
    /// @return a new context deriving from this one, with the variable set
    public OverlayEvaluationContext createOverlay(String name, Object value) {
        final var ctx = new OverlayEvaluationContext(this);
        ctx.setVariable(name, value);
        return ctx;
    }

    /// @return the bean expression context of the bean factory, through which beans are properties of the
    /// root object; a null root without a bean factory
    @Override
    public TypedValue getRootObject() {
        return this.rootObject;
    }

    /// @return the resolver of `@name` references, or `null` without a bean factory
    @Override
    public BeanResolver getBeanResolver() {
        return this.beanResolver;
    }

    /// @return a standard type locator, using the class loader of the bean factory when there is one
    @Override
    public TypeLocator getTypeLocator() {
        return this.typeLocator;
    }

    /// @return a converter using the conversion service of the bean factory, or the shared default one
    @Override
    public TypeConverter getTypeConverter() {
        return this.typeConverter;
    }

    /// @return the standard type comparator
    @Override
    public TypeComparator getTypeComparator() {
        return TYPE_COMPARATOR;
    }

    /// @return the standard operator overloader, which overloads nothing
    @Override
    public OperatorOverloader getOperatorOverloader() {
        return OPERATOR_OVERLOADER;
    }

    /// @return the accessors of bean expression contexts, bean factories, maps, environments and,
    /// reflectively, of any object
    @Override
    public List<PropertyAccessor> getPropertyAccessors() {
        return PROPERTY_ACCESSORS;
    }

    /// @return no custom index accessors: SpEL built-in indexing of arrays, lists and maps still applies
    @Override
    public List<IndexAccessor> getIndexAccessors() {
        return INDEX_ACCESSORS;
    }

    /// @return the reflective constructor resolver
    @Override
    public List<ConstructorResolver> getConstructorResolvers() {
        return CTOR_RESOLVERS;
    }

    /// @return the reflective method resolver
    @Override
    public List<MethodResolver> getMethodResolvers() {
        return METHOD_RESOLVERS;
    }

}
