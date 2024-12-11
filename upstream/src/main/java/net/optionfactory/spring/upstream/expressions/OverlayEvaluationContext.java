package net.optionfactory.spring.upstream.expressions;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.expression.BeanExpressionContextAccessor;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.context.expression.MapAccessor;
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
import org.springframework.expression.spel.support.ReflectiveConstructorResolver;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.expression.spel.support.StandardOperatorOverloader;
import org.springframework.expression.spel.support.StandardTypeComparator;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;

public class OverlayEvaluationContext implements EvaluationContext {

    private static final List<PropertyAccessor> PROPERTY_ACCESSORS = List.of(
            new BeanExpressionContextAccessor(),
            new BeanFactoryAccessor(),
            new MapAccessor(),
            new EnvironmentAccessor()
    );
    private static final List<IndexAccessor> INDEX_ACCESSORS = List.of(
    
    );
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
    private final ConcurrentLinkedDeque<ConcurrentHashMap<String, Object>> variables;

    public OverlayEvaluationContext(ConfigurableBeanFactory beanFactory) {
        this.rootObject = new TypedValue(beanFactory == null ? null : new BeanExpressionContext(beanFactory, null));
        this.beanResolver = beanFactory != null ? new BeanFactoryResolver(beanFactory) : null;
        this.typeLocator = new StandardTypeLocator(beanFactory != null ? beanFactory.getBeanClassLoader() : null);
        this.typeConverter = new StandardTypeConverter(() -> {
            ConversionService cs = beanFactory != null ? beanFactory.getConversionService() : null;
            return (cs != null ? cs : DefaultConversionService.getSharedInstance());
        });
        this.variables = new ConcurrentLinkedDeque<>();
        this.variables.add(new ConcurrentHashMap<>());
    }

    public OverlayEvaluationContext(OverlayEvaluationContext other) {
        this.rootObject = other.rootObject;
        this.beanResolver = other.beanResolver;
        this.typeLocator = other.typeLocator;
        this.typeConverter = other.typeConverter;
        this.variables = new ConcurrentLinkedDeque<>();
        this.variables.addAll(other.variables);
        this.variables.add(new ConcurrentHashMap<>());
    }

    @Override
    public void setVariable(String name, Object value) {
        if (name != null) {
            final var latestOveraly = this.variables.getLast();
            if (value != null) {
                latestOveraly.put(name, value);
            } else {
                latestOveraly.remove(name);
            }
        }
    }

    public void setVariables(Map<String, Object> values) {
        this.variables.getLast().putAll(values);
    }

    @Override
    public Object lookupVariable(String name) {
        for (ConcurrentHashMap<String, Object> overlay : variables.reversed()) {
            final var value = overlay.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public boolean isAssignmentEnabled() {
        return false;
    }

    public OverlayEvaluationContext createOverlay() {
        return new OverlayEvaluationContext(this);
    }

    public OverlayEvaluationContext createOverlay(String name, Object value) {
        final var ctx = new OverlayEvaluationContext(this);
        ctx.setVariable(name, value);
        return ctx;
    }

    @Override
    public TypedValue getRootObject() {
        return this.rootObject;
    }

    @Override
    public BeanResolver getBeanResolver() {
        return this.beanResolver;
    }

    @Override
    public TypeLocator getTypeLocator() {
        return this.typeLocator;
    }

    @Override
    public TypeConverter getTypeConverter() {
        return this.typeConverter;
    }

    @Override
    public TypeComparator getTypeComparator() {
        return TYPE_COMPARATOR;
    }

    @Override
    public OperatorOverloader getOperatorOverloader() {
        return OPERATOR_OVERLOADER;
    }

    @Override
    public List<PropertyAccessor> getPropertyAccessors() {
        return PROPERTY_ACCESSORS;
    }

    @Override
    public List<IndexAccessor> getIndexAccessors() {
        return INDEX_ACCESSORS;
    }

    @Override
    public List<ConstructorResolver> getConstructorResolvers() {
        return CTOR_RESOLVERS;
    }

    @Override
    public List<MethodResolver> getMethodResolvers() {
        return METHOD_RESOLVERS;
    }

}
