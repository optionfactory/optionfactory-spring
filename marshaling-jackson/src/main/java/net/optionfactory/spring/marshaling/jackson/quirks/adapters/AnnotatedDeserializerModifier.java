package net.optionfactory.spring.marshaling.jackson.quirks.adapters;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;

public class AnnotatedDeserializerModifier extends BeanDeserializerModifier {

    private final List<QuirkHandler> transformers;

    public AnnotatedDeserializerModifier(List<QuirkHandler> transformers) {
        this.transformers = transformers;
    }

    private static <T> List<T> asList(Iterator<T> iter) {
        final var r = new ArrayList<T>();
        while (iter.hasNext()) {
            r.add(iter.next());
        }
        return r;
    }

    @Override
    public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription bd, BeanDeserializerBuilder builder) {
        final var props = asList(builder.getProperties());        
        props.forEach(prop -> {
            transformers.stream().forEach(handler -> {
                final var ann = prop.getAnnotation(handler.annotation());
                if (ann == null) {
                    return;
                }
                final var transformed = handler.deserialization(ann, prop);
                builder.addOrReplaceProperty(transformed, true);
            });
        });
        //see https://github.com/FasterXML/jackson-databind/issues/3981
        if (builder.getValueInstantiator() instanceof StdValueInstantiator vi && vi.canCreateFromObjectWith()) {
            SettableBeanProperty[] instantiatorProperties = vi.getFromObjectArguments(config);
            if (instantiatorProperties != null && instantiatorProperties.length > 0) {
                // replace all creator properties instantiator to use replacementDeserializer
                final var modifiedProperties = Arrays.stream(instantiatorProperties).map(prop -> {
                    for (final var transformer : transformers) {
                        final var ann = prop.getAnnotation(transformer.annotation());
                        if (ann == null) {
                            continue;
                        }
                        prop = transformer.deserialization(ann, prop);
                    }
                    return prop;
                }).toArray(length -> new SettableBeanProperty[length]);
                // configure valueInstantiator to use the modified properties
                vi.configureFromObjectSettings(
                        vi.getDefaultCreator(),
                        vi.getDelegateCreator(),
                        vi.getDelegateType(config),
                        new SettableBeanProperty[0] /*Not really sure what to do here*/,
                        vi.getWithArgsCreator(),
                        modifiedProperties
                );
            }
        }
        return builder;
    }

}
