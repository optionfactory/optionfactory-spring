package net.optionfactory.spring.marshaling.jackson.quirks.adapters;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.std.StdValueInstantiator;

/// Applies [QuirkHandler]s to the properties of every bean deserializer jackson builds, installed
/// by [net.optionfactory.spring.marshaling.jackson.quirks.Quirks.Builder#build].
///
/// Each property is passed through every handler whose annotation it carries, in list order. The
/// properties bound through a creator (a record's canonical constructor, a `@JsonCreator`) are
/// handled as well: replacing the builder's properties does not reach them (see
/// [jackson-databind#3981](https://github.com/FasterXML/jackson-databind/issues/3981)), so they are
/// reconfigured on the `StdValueInstantiator`. That requires reading its private
/// `_delegateArguments` field by reflection, which fails with an `IllegalStateException` when deep
/// reflection on `tools.jackson.databind` is not allowed, e.g. when it is a named module that does
/// not open the package.
///
/// A property renamed by a handler is added under its new name, while a field or setter property
/// also stays reachable under its original one.
public class AnnotatedDeserializerModifier extends ValueDeserializerModifier {

    private final List<QuirkHandler<?>> transformers;

    /// @param transformers the handlers to apply, used as given rather than copied
    public AnnotatedDeserializerModifier(List<QuirkHandler<?>> transformers) {
        this.transformers = transformers;
    }

    private static <T> List<T> asList(Iterator<T> iter) {
        final var r = new ArrayList<T>();
        while (iter.hasNext()) {
            r.add(iter.next());
        }
        return r;
    }

    private <A extends Annotation> SettableBeanProperty transform(QuirkHandler<A> handler, SettableBeanProperty prop) {
        final A ann = prop.getAnnotation(handler.annotation());
        if (ann == null) {
            return prop;
        }
        return handler.deserialization(ann, prop);
    }

    /// Replaces the bean's properties, and its creator properties, with the ones the handlers
    /// return.
    ///
    /// @param config the deserialization config
    /// @param bd the bean description
    /// @param builder the deserializer builder, whose properties are replaced
    /// @return the same builder
    /// @throws IllegalStateException when the creator properties cannot be reconfigured by
    /// reflection
    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription.Supplier bd, BeanDeserializerBuilder builder) {
        final var props = asList(builder.getProperties());
        props.forEach(prop -> {
            SettableBeanProperty currentProp = prop;
            for (QuirkHandler<?> handler : transformers) {
                currentProp = transform(handler, currentProp);
            }
            if (currentProp != prop) {
                builder.addOrReplaceProperty(currentProp, true);
            }
        });

        if (builder.getValueInstantiator() instanceof StdValueInstantiator vi && vi.canCreateFromObjectWith()) {
            SettableBeanProperty[] instantiatorProperties = vi.getFromObjectArguments(config);
            if (instantiatorProperties != null && instantiatorProperties.length > 0) {
                final var modifiedProperties = Arrays.stream(instantiatorProperties).map(prop -> {
                    SettableBeanProperty currentProp = prop;
                    for (final var transformer : transformers) {
                        currentProp = transform(transformer, currentProp);
                    }
                    return currentProp;
                }).toArray(length -> new SettableBeanProperty[length]);

                SettableBeanProperty[] delegateArgs;
                try {
                    final var delegateArgsField = StdValueInstantiator.class.getDeclaredField("_delegateArguments");
                    delegateArgsField.setAccessible(true);
                    delegateArgs = (SettableBeanProperty[]) delegateArgsField.get(vi);
                } catch (Exception ex) {
                    throw new IllegalStateException(
                        "QuirksModule reflection hack failed. Cannot access internal Jackson field '_delegateArguments'. " +
                        "Ensure your JVM parameters allow deep reflection access to tools.jackson.databind.", ex
                    );
                }

                vi.configureFromObjectSettings(
                        vi.getDefaultCreator(),
                        vi.getDelegateCreator(),
                        vi.getDelegateType(config),
                        delegateArgs,
                        vi.getWithArgsCreator(),
                        modifiedProperties
                );
            }
        }
        return builder;
    }
}