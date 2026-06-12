package net.optionfactory.spring.marshaling.jackson.quirks.adapters;

import java.lang.annotation.Annotation;
import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.BeanSerializerBuilder;
import tools.jackson.databind.ser.ValueSerializerModifier;

public class AnnotatedSerializerModifier extends ValueSerializerModifier {

    private final List<QuirkHandler<?>> modifiers;

    public AnnotatedSerializerModifier(List<QuirkHandler<?>> modifiers) {
        this.modifiers = modifiers;
    }

    private <A extends Annotation> BeanPropertyWriter transform(QuirkHandler<A> handler, BeanPropertyWriter pw) {
        final A ann = pw.getAnnotation(handler.annotation());
        if (ann == null) {
            return pw;
        }
        return handler.serialization(ann, pw);
    }

    @Override
    public BeanSerializerBuilder updateBuilder(SerializationConfig config, BeanDescription.Supplier bd, BeanSerializerBuilder builder) {
        final var mapped = builder.getProperties().stream().map(pw -> {
            BeanPropertyWriter current = pw;
            for (final var entry : modifiers) {
                current = transform(entry, current);
            }
            return current;
        }).toList();

        builder.setProperties(mapped);
        return builder;
    }
}
