package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.Instant;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

/// Handles [Quirks.Timestamp]: see the annotation for the representation, and for the lack of a
/// check on the property type.
public class TimestampQuirkHandler implements QuirkHandler<Quirks.Timestamp> {

    /// @return [Quirks.Timestamp]
    @Override
    public Class<Quirks.Timestamp> annotation() {
        return Quirks.Timestamp.class;
    }

    /// @param ann the annotation, with the unit
    /// @param bpw the writer of the property
    /// @return the same writer, with a [Serializer] assigned
    @Override
    public BeanPropertyWriter serialization(Quirks.Timestamp ann, BeanPropertyWriter bpw) {
        bpw.assignSerializer(new Serializer(ann.millis()));
        return bpw;
    }

    /// @param ann the annotation, with the unit
    /// @param sbp the property
    /// @return a copy of the property with a [Deserializer]
    @Override
    public SettableBeanProperty deserialization(Quirks.Timestamp ann, SettableBeanProperty sbp) {
        return sbp.withValueDeserializer(new Deserializer(ann.millis()));
    }

    /// Reads an `Instant` from an integer, or a string holding one, counting milliseconds or
    /// seconds since the epoch.
    public static class Deserializer extends ValueDeserializer<Instant> {

        private final boolean millis;

        /// @param millis true for milliseconds, false for seconds
        public Deserializer(boolean millis) {
            this.millis = millis;
        }

        /// @param jp the parser, on the value token
        /// @param dc the deserialization context
        /// @return the instant
        /// @throws tools.jackson.databind.exc.MismatchedInputException for a token that is neither
        /// an integer nor a string, and for a blank or non-integer string
        @Override
        public Instant deserialize(JsonParser jp, DeserializationContext dc) {
            final var token = jp.currentToken();

            if (token == JsonToken.VALUE_NUMBER_INT) {
                long value = jp.getLongValue();
                return millis ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
            }

            if (token == JsonToken.VALUE_STRING) {
                final var text = jp.getValueAsString();
                if (text == null || text.isBlank()) {
                    return dc.reportInputMismatch(Instant.class, "Blank or missing text for timestamp field.");
                }
                try {
                    long value = Long.parseLong(text.trim());
                    return millis ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
                } catch (NumberFormatException e) {
                    return dc.reportInputMismatch(Instant.class, "Malformed numeric string for timestamp field: '%s'", text);
                }
            }

            return dc.reportInputMismatch(Instant.class, "Invalid token type for timestamp. Expected a numeric value or numeric string, but got: %s", token);
        }

        /// @param ctxt the deserialization context
        /// @return `null`
        @Override
        public Instant getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }

    /// Writes an `Instant` as milliseconds or seconds since the epoch.
    public static class Serializer extends ValueSerializer<Object> {

        private final boolean millis;

        /// @param millis true for milliseconds, false for seconds
        public Serializer(boolean millis) {
            this.millis = millis;
        }

        /// Writes nothing at all for a value that is not an `Instant`, leaving the property without
        /// a value and the json invalid.
        ///
        /// @param value the instant
        /// @param gen the generator
        /// @param ctxt the serialization context
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value instanceof Instant instant) {
                gen.writeNumber(millis ? instant.toEpochMilli() : instant.getEpochSecond());
            }
        }
    }
}
