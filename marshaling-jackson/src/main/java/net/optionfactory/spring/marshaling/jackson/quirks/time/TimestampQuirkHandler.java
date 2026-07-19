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

public class TimestampQuirkHandler implements QuirkHandler<Quirks.Timestamp> {

    @Override
    public Class<Quirks.Timestamp> annotation() {
        return Quirks.Timestamp.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Timestamp ann, BeanPropertyWriter bpw) {
        bpw.assignSerializer(new Serializer(ann.millis()));
        return bpw;
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Timestamp ann, SettableBeanProperty sbp) {
        return sbp.withValueDeserializer(new Deserializer(ann.millis()));
    }

    public static class Deserializer extends ValueDeserializer<Instant> {

        private final boolean millis;

        public Deserializer(boolean millis) {
            this.millis = millis;
        }

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

        @Override
        public Instant getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }

    public static class Serializer extends ValueSerializer<Object> {

        private final boolean millis;

        public Serializer(boolean millis) {
            this.millis = millis;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value instanceof Instant instant) {
                gen.writeNumber(millis ? instant.toEpochMilli() : instant.getEpochSecond());
            }
        }
    }
}
