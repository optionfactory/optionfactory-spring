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

public class BooleanQuirkHandler implements QuirkHandler<Quirks.Bool> {

    @Override
    public Class<Quirks.Bool> annotation() {
        return Quirks.Bool.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Bool ann, BeanPropertyWriter bpw) {
        bpw.assignSerializer((ValueSerializer) new Serializer(ann.t(), ann.f()));
        return bpw;
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Bool ann, SettableBeanProperty sbp) {
        final var nullable = sbp.getType().getRawClass() == Boolean.class;
        final var deserializer = new Deserializer(ann.t(), nullable);
        return sbp.withValueDeserializer(deserializer);
    }

    public static class Deserializer extends ValueDeserializer<Boolean> {

        private final String t;
        private final boolean nullable;

        public Deserializer(String t, boolean nullable) {
            this.t = t;
            this.nullable = nullable;
        }

        @Override
        public Boolean deserialize(JsonParser jp, DeserializationContext dc) {
            return t.equals(jp.getText());
        }

        @Override
        public Boolean getNullValue(DeserializationContext ctxt) {
            return nullable ? null : false;
        }

    }

    public static class Serializer extends ValueSerializer<Boolean> {

        private final String t;
        private final String f;

        public Serializer(String t, String f) {
            this.t = t;
            this.f = f;
        }

        @Override
        public void serialize(Boolean v, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeString(v ? t : f);
        }

    }

}
