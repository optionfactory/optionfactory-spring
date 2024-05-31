package net.optionfactory.spring.marshaling.jackson.quirks.text;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import java.io.IOException;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;

public class TrimQuirkHandler implements QuirkHandler<Quirks.Trim> {

    @Override
    public Class<Quirks.Trim> annotation() {
        return Quirks.Trim.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Trim ann, BeanPropertyWriter bpw) {
        bpw.assignSerializer((JsonSerializer) Serializer.INSTANCE);
        return bpw;
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Trim ann, SettableBeanProperty sbp) {
        return sbp.withValueDeserializer(Deserializer.INSTANCE);
    }

    public static class Serializer extends JsonSerializer<String> {

        public static Serializer INSTANCE = new Serializer();

        @Override
        public void serialize(String t, JsonGenerator jg, SerializerProvider sp) throws IOException {
            jg.writeString(t.trim());
        }

    }

    public static class Deserializer extends JsonDeserializer<String> {

        public static Deserializer INSTANCE = new Deserializer();

        @Override
        public String deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            return jp.getText().trim();
        }

        @Override
        public String getNullValue(DeserializationContext ctxt) throws JsonMappingException {
            return null;
        }

    }

}
