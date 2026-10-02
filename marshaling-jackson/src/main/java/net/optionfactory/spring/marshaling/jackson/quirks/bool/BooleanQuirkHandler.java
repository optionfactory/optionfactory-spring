package net.optionfactory.spring.marshaling.jackson.quirks.bool;

import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

/// Handles [Quirks.Bool]: see the annotation for the representation.
public class BooleanQuirkHandler implements QuirkHandler<Quirks.Bool> {

    /// @return [Quirks.Bool]
    @Override
    public Class<Quirks.Bool> annotation() {
        return Quirks.Bool.class;
    }

    /// @param ann the annotation, with the two strings
    /// @param bpw the writer of the property
    /// @return the same writer, with a [Serializer] assigned
    /// @throws IllegalStateException when the property is neither `boolean` nor `Boolean`
    @Override
    public BeanPropertyWriter serialization(Quirks.Bool ann, BeanPropertyWriter bpw) {
        Class<?> raw = bpw.getType().getRawClass();
        if (raw != boolean.class && raw != Boolean.class) {
            throw new IllegalStateException(String.format(
                    "Invalid @Quirks.Bool placement on property '%s'. Can only be applied to boolean/Boolean fields, but found type: %s",
                    bpw.getName(), raw.getName()
            ));
        }
        bpw.assignSerializer(new Serializer(ann.t(), ann.f()));
        return bpw;
    }

    /// @param ann the annotation, with the two strings
    /// @param sbp the property
    /// @return a copy of the property with a [Deserializer], rejecting `null` for a `boolean`
    /// @throws IllegalStateException when the property is neither `boolean` nor `Boolean`
    @Override
    public SettableBeanProperty deserialization(Quirks.Bool ann, SettableBeanProperty sbp) {
        Class<?> raw = sbp.getType().getRawClass();
        if (raw != boolean.class && raw != Boolean.class) {
            throw new IllegalStateException(String.format(
                    "Invalid @Quirks.Bool placement on property '%s'. Can only be applied to boolean/Boolean fields, but found type: %s",
                    sbp.getName(), raw.getName()
            ));
        }        
        final var nullable = sbp.getType().getRawClass() == Boolean.class;
        final var deserializer = new Deserializer(ann.t(), ann.f(), nullable);
        return sbp.withValueDeserializer(deserializer);
    }

    /// Reads a boolean from the text of a scalar token matching one of two strings.
    public static class Deserializer extends ValueDeserializer<Boolean> {

        private final String t;
        private final String f;
        private final boolean nullable;

        /// @param t the string representing `true`
        /// @param f the string representing `false`
        /// @param nullable whether a json `null` is accepted, as for a `Boolean` property
        public Deserializer(String t, String f, boolean nullable) {
            this.t = t;
            this.f = f;
            this.nullable = nullable;
        }

        /// @param jp the parser, on the value token
        /// @param dc the deserialization context
        /// @return `true` or `false`, when the token's text equals the corresponding string
        /// @throws tools.jackson.databind.exc.MismatchedInputException when it equals neither
        @Override
        public Boolean deserialize(JsonParser jp, DeserializationContext dc) {
            final var text = jp.getValueAsString();
            if (t.equals(text)) {
                return true;
            }
            if (f.equals(text)) {
                return false;
            }
            return dc.reportInputMismatch(Boolean.class, "Invalid value for @Quirks.Bool field. Expected token matching '%s' or '%s' got: '%s'", t, f, text);
        }

        /// The value of a json `null`, also used for a missing creator property.
        ///
        /// @param dc the deserialization context
        /// @return `null`, when nullable
        /// @throws tools.jackson.databind.exc.MismatchedInputException when not nullable
        @Override
        public Boolean getNullValue(DeserializationContext dc) {
            if (!nullable) {
                return dc.reportInputMismatch(Boolean.class, "Invalid null value for non-nullable @Quirks.Bool primitive field.");
            }
            return null;
        }

    }

    /// Writes a boolean as one of two strings.
    public static class Serializer extends ValueSerializer<Object> {

        private final String t;
        private final String f;

        /// @param t the string written for `true`
        /// @param f the string written for `false`
        public Serializer(String t, String f) {
            this.t = t;
            this.f = f;
        }

        /// @param v the value; anything but `Boolean.TRUE` is written as the `false` string
        /// @param gen the generator
        /// @param ctxt the serialization context
        @Override
        public void serialize(Object v, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            boolean value = Boolean.TRUE.equals(v);
            gen.writeString(value ? t : f);
        }

    }

}
