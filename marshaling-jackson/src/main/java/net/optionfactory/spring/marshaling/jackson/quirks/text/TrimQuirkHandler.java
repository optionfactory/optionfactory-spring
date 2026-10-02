package net.optionfactory.spring.marshaling.jackson.quirks.text;

import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

/// Handles [Quirks.Trim]: see the annotation for the accepted tokens.
public class TrimQuirkHandler implements QuirkHandler<Quirks.Trim> {

    /// @return [Quirks.Trim]
    @Override
    public Class<Quirks.Trim> annotation() {
        return Quirks.Trim.class;
    }

    /// @param ann the annotation
    /// @param bpw the writer of the property
    /// @return the same writer, with the trimming [Serializer] assigned
    /// @throws IllegalStateException when the property is not a `String`
    @Override
    public BeanPropertyWriter serialization(Quirks.Trim ann, BeanPropertyWriter bpw) {
        if (bpw.getType().getRawClass() != String.class) {
            throw new IllegalStateException(String.format(
                    "Invalid @Quirks.Trim placement on property '%s'. Can only be applied to String properties, but found type: %s",
                    bpw.getName(), bpw.getType().getRawClass().getName()
            ));
        }
        bpw.assignSerializer(Serializer.INSTANCE);
        return bpw;
    }

    /// @param ann the annotation
    /// @param sbp the property
    /// @return a copy of the property with the trimming [Deserializer]
    /// @throws IllegalStateException when the property is not a `String`
    @Override
    public SettableBeanProperty deserialization(Quirks.Trim ann, SettableBeanProperty sbp) {
        if (sbp.getType().getRawClass() != String.class) {
            throw new IllegalStateException(String.format(
                    "Invalid @Quirks.Trim placement on property '%s'. Can only be applied to String properties, but found type: %s",
                    sbp.getName(), sbp.getType().getRawClass().getName()
            ));
        }
        return sbp.withValueDeserializer(Deserializer.INSTANCE);
    }

    /// Writes a string trimmed; stateless, use [#INSTANCE].
    public static class Serializer extends ValueSerializer<Object> {

        /// The shared instance.
        public static final Serializer INSTANCE = new Serializer();

        /// @param t the string
        /// @param jg the generator
        /// @param sc the serialization context
        @Override
        public void serialize(Object t, JsonGenerator jg, SerializationContext sc) {
            jg.writeString(((String) t).trim());
        }
    }

    /// Reads a string token trimmed; stateless, use [#INSTANCE].
    public static class Deserializer extends ValueDeserializer<String> {

        /// The shared instance.
        public static final Deserializer INSTANCE = new Deserializer();

        /// @param jp the parser, on the value token
        /// @param dc the deserialization context
        /// @return the trimmed string, empty for a blank one
        /// @throws tools.jackson.databind.exc.MismatchedInputException for a token that is not a
        /// string
        @Override
        public String deserialize(JsonParser jp, DeserializationContext dc) {
            if (!jp.hasToken(tools.jackson.core.JsonToken.VALUE_STRING)) {
                return dc.reportInputMismatch(String.class, "Expected a string text token for @Quirks.Trim field, got: %s", jp.currentToken());
            }
            return jp.getValueAsString().trim();
        }

        /// @param ctxt the deserialization context
        /// @return `null`
        @Override
        public String getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
