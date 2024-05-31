package net.optionfactory.spring.marshaling.jackson.quirks.adapters;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanSerializerBuilder;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;

public class AnnotatedSeralizerModifier extends BeanSerializerModifier {

    private final List<QuirkHandler> modifiers;

    public AnnotatedSeralizerModifier(List<QuirkHandler> modifiers) {
        this.modifiers = modifiers;
    }

    @Override
    public BeanSerializerBuilder updateBuilder(SerializationConfig config, BeanDescription bd, BeanSerializerBuilder builder) {
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
