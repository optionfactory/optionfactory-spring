package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

public class LocalDateAsIsoInstantQuirkHandler implements QuirkHandler<Quirks.LocalDateAsIsoInstant> {

    @Override
    public Class<Quirks.LocalDateAsIsoInstant> annotation() {
        return Quirks.LocalDateAsIsoInstant.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.LocalDateAsIsoInstant ann, BeanPropertyWriter bpw) {
        final Class<?> raw = bpw.getType().getRawClass();
        if (raw != LocalDate.class) {
            throw new IllegalStateException(String.format(
                    "Invalid @LocalDateAsIsoInstant placement on property '%s'. Can only be applied to LocalDate fields, but found type: %s",
                    bpw.getName(), raw.getName()
            ));
        }
        final var zoneId = ZoneId.of(ann.value());
        final var instantOffset = new Offset(ann.ioffset(), ann.iunit());
        final var localDateOffset = new Offset(ann.ldoffset(), ann.ldunit());
        bpw.assignSerializer(new Serializer(zoneId, instantOffset, localDateOffset));
        return bpw;
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.LocalDateAsIsoInstant ann, SettableBeanProperty sbp) {
        final Class<?> raw = sbp.getType().getRawClass();
        if (raw != LocalDate.class) {
            throw new IllegalStateException(String.format(
                    "Invalid @LocalDateAsIsoInstant placement on property '%s'. Can only be applied to LocalDate fields, but found type: %s",
                    sbp.getName(), raw.getName()
            ));
        }
        final var zoneId = ZoneId.of(ann.value());
        final var instantOffset = new Offset(ann.ioffset(), ann.iunit());
        final var localDateOffset = new Offset(ann.ldoffset(), ann.ldunit());
        return sbp.withValueDeserializer(new Deserializer(zoneId, instantOffset, localDateOffset, raw));
    }

    public record Offset(int amount, ChronoUnit unit) {

    }

    public static class Deserializer extends ValueDeserializer<LocalDate> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;
        private final Class<?> targetType;

        public Deserializer(ZoneId zid, Offset instantOffset, Offset localDateOffset, Class<?> targetType) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
            this.targetType = targetType;
        }

        @Override
        public LocalDate deserialize(JsonParser jp, DeserializationContext dc) {
            if (!jp.hasToken(JsonToken.VALUE_STRING)) {
                return dc.reportInputMismatch(targetType, "Expected a string token representing an ISO instant, but got: %s", jp.currentToken());
            }
            final String text = jp.getValueAsString();
            if (text.isBlank()) {
                return dc.reportInputMismatch(targetType, "Blank or missing text for ISO instant property.");
            }
            try {
                return Instant.parse(text)
                        .minus(instantOffset.amount(), instantOffset.unit())
                        .atZone(zid)
                        .toLocalDate()
                        .minus(localDateOffset.amount(), localDateOffset.unit());
            } catch (Exception e) {
                return dc.reportInputMismatch(targetType, "Malformed ISO instant text value: '%s'", text);
            }
        }

        @Override
        public LocalDate getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }

    public static class Serializer extends ValueSerializer<Object> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;

        public Serializer(ZoneId zid, Offset instantOffset, Offset localDateOffset) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
            final var asIsoInstant = ((LocalDate) value)
                    .plus(localDateOffset.amount(), localDateOffset.unit())
                    .atStartOfDay(zid)
                    .toInstant()
                    .plus(instantOffset.amount(), instantOffset.unit())
                    .toString();
            gen.writeString(asIsoInstant);
        }
    }
}
