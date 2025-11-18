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

public class TrimQuirkHandler implements QuirkHandler<Quirks.Trim> {

    @Override
    public Class<Quirks.Trim> annotation() {
        return Quirks.Trim.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Trim ann, BeanPropertyWriter bpw) {
        bpw.assignSerializer((ValueSerializer) Serializer.INSTANCE);
        return bpw;
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Trim ann, SettableBeanProperty sbp) {
        return sbp.withValueDeserializer(Deserializer.INSTANCE);
    }

    public static class Serializer extends ValueSerializer<String> {

        public static Serializer INSTANCE = new Serializer();

        @Override
        public void serialize(String t, JsonGenerator jg, SerializationContext sc) {
            jg.writeString(t.trim());
        }

    }

    public static class Deserializer extends ValueDeserializer<String> {

        public static Deserializer INSTANCE = new Deserializer();

        @Override
        public String deserialize(JsonParser jp, DeserializationContext dc) {
            return jp.getString().trim();
        }

        @Override
        public String getNullValue(DeserializationContext ctxt) {
            return null;
        }

    }

}
