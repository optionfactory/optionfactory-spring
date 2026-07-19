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

    public static class Deserializer extends ValueDeserializer<Boolean> {

        private final String t;
        private final String f;
        private final boolean nullable;

        public Deserializer(String t, String f, boolean nullable) {
            this.t = t;
            this.f = f;
            this.nullable = nullable;
        }

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

        @Override
        public Boolean getNullValue(DeserializationContext dc) {
            if (!nullable) {
                return dc.reportInputMismatch(Boolean.class, "Invalid null value for non-nullable @Quirks.Bool primitive field.");
            }
            return null;
        }

    }

    public static class Serializer extends ValueSerializer<Object> {

        private final String t;
        private final String f;

        public Serializer(String t, String f) {
            this.t = t;
            this.f = f;
        }

        @Override
        public void serialize(Object v, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            boolean value = Boolean.TRUE.equals(v);
            gen.writeString(value ? t : f);
        }

    }

}
