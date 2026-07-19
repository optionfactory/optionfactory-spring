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
        if (bpw.getType().getRawClass() != String.class) {
            throw new IllegalStateException(String.format(
                    "Invalid @Quirks.Trim placement on property '%s'. Can only be applied to String properties, but found type: %s",
                    bpw.getName(), bpw.getType().getRawClass().getName()
            ));
        }
        bpw.assignSerializer(Serializer.INSTANCE);
        return bpw;
    }

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

    public static class Serializer extends ValueSerializer<Object> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void serialize(Object t, JsonGenerator jg, SerializationContext sc) {
            jg.writeString(((String) t).trim());
        }
    }

    public static class Deserializer extends ValueDeserializer<String> {

        public static final Deserializer INSTANCE = new Deserializer();

        @Override
        public String deserialize(JsonParser jp, DeserializationContext dc) {
            if (!jp.hasToken(tools.jackson.core.JsonToken.VALUE_STRING)) {
                return dc.reportInputMismatch(String.class, "Expected a string text token for @Quirks.Trim field, got: %s", jp.currentToken());
            }
            return jp.getValueAsString().trim();
        }

        @Override
        public String getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
