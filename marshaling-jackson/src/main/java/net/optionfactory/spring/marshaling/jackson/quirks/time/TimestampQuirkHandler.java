package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.Instant;
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
            // getValueAsLong() automatically handles numeric tokens and stringified numeric tokens safely
            long value = jp.getValueAsLong(); 
            return millis ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
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
                gen.writeNumber(millis ? instant.toEpochMilli(): instant.getEpochSecond());
            }
        }
    }
}