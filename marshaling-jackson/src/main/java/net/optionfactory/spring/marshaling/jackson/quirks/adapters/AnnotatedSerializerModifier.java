package net.optionfactory.spring.marshaling.jackson.quirks.adapters;

import java.lang.annotation.Annotation;
import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.BeanSerializerBuilder;
import tools.jackson.databind.ser.ValueSerializerModifier;

/// Applies [QuirkHandler]s to the properties of every bean serializer jackson builds, installed by
/// [net.optionfactory.spring.marshaling.jackson.quirks.Quirks.Builder#build].
///
/// Each property is passed through every handler whose annotation it carries, in list order.
public class AnnotatedSerializerModifier extends ValueSerializerModifier {

    private final List<QuirkHandler<?>> modifiers;

    /// @param modifiers the handlers to apply, used as given rather than copied
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

    /// Replaces the bean's property writers with the ones the handlers return.
    ///
    /// @param config the serialization config
    /// @param bd the bean description
    /// @param builder the serializer builder, whose properties are replaced
    /// @return the same builder
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
