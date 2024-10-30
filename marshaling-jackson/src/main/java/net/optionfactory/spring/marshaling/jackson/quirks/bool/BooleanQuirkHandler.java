package net.optionfactory.spring.marshaling.jackson.quirks.bool;

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

public class BooleanQuirkHandler implements QuirkHandler<Quirks.Bool> {

    @Override
    public Class<Quirks.Bool> annotation() {
        return Quirks.Bool.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Bool ann, BeanPropertyWriter bpw) {
        bpw.assignSerializer((JsonSerializer) new Serializer(ann.t(), ann.f()));
        return bpw;
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Bool ann, SettableBeanProperty sbp) {
        final var nullable = sbp.getType().getRawClass() == Boolean.class;
        final var deserializer = new Deserializer(ann.t(), nullable);
        return sbp.withValueDeserializer(deserializer);
    }

    public static class Deserializer extends JsonDeserializer<Boolean> {

        private final String t;
        private final boolean nullable;

        public Deserializer(String t, boolean nullable) {
            this.t = t;
            this.nullable = nullable;
        }

        @Override
        public Boolean deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            return t.equals(jp.getText());
        }

        @Override
        public Boolean getNullValue(DeserializationContext ctxt) throws JsonMappingException {
            return nullable ? null : false;
        }

    }

    public static class Serializer extends JsonSerializer<Boolean> {

        private final String t;
        private final String f;

        public Serializer(String t, String f) {
            this.t = t;
            this.f = f;
        }

        @Override
        public void serialize(Boolean v, JsonGenerator jg, SerializerProvider sp) throws IOException {
            jg.writeString(v ? t : f);
        }

    }

}
