package net.optionfactory.spring.marshaling.jackson.quirks.adapters;

import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanSerializerBuilder;
import tools.jackson.databind.ser.ValueSerializerModifier;

public class AnnotatedSeralizerModifier extends ValueSerializerModifier {

    private final List<QuirkHandler> modifiers;

    public AnnotatedSeralizerModifier(List<QuirkHandler> modifiers) {
        this.modifiers = modifiers;
    }

    @Override
    public BeanSerializerBuilder updateBuilder(SerializationConfig config, BeanDescription.Supplier bd, BeanSerializerBuilder builder) {
        final var mapped = builder.getProperties().stream().map(pw -> {
            for (final var entry : modifiers) {
                final var ann = pw.getAnnotation(entry.annotation());
                if (ann == null) {
                    continue;
                }
                pw = entry.serialization(ann, pw);
            }
            return pw;
        }).toList();
        builder.setProperties(mapped);
        return builder;
    }

}
