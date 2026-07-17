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

public class AnnotatedDeserializerModifier extends ValueDeserializerModifier {

    private final List<QuirkHandler<?>> transformers;

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
        //see https://github.com/FasterXML/jackson-databind/issues/3981
        if (builder.getValueInstantiator() instanceof StdValueInstantiator vi && vi.canCreateFromObjectWith()) {
            SettableBeanProperty[] instantiatorProperties = vi.getFromObjectArguments(config);
            if (instantiatorProperties != null && instantiatorProperties.length > 0) {
                // replace all creator properties instantiator to use replacementDeserializer
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
                    delegateArgs = null;
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
