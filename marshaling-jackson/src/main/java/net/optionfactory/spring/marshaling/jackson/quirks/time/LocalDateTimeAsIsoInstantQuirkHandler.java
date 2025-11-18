package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

public class LocalDateTimeAsIsoInstantQuirkHandler implements QuirkHandler<Quirks.LocalDateTimeAsIsoInstant> {

    @Override
    public Class<Quirks.LocalDateTimeAsIsoInstant> annotation() {
        return Quirks.LocalDateTimeAsIsoInstant.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.LocalDateTimeAsIsoInstant ann, BeanPropertyWriter bpw) {
        final var zoneId = ZoneId.of(ann.value());
        final var instantOffset = new Offset(ann.ioffset(), ann.iunit());
        final var localDateOffset = new Offset(ann.ldoffset(), ann.ldunit());
        final var serializer = new Serializer(zoneId, instantOffset, localDateOffset);
        bpw.assignSerializer((ValueSerializer) serializer);
        return bpw;

    }

    @Override
    public SettableBeanProperty deserialization(Quirks.LocalDateTimeAsIsoInstant ann, SettableBeanProperty sbp) {
        final var zoneId = ZoneId.of(ann.value());
        final var instantOffset = new Offset(ann.ioffset(), ann.iunit());
        final var localDateOffset = new Offset(ann.ldoffset(), ann.ldunit());
        final var deserializer = new Deserializer(zoneId, instantOffset, localDateOffset);
        return sbp.withValueDeserializer(deserializer);
    }

    public record Offset(int amount, ChronoUnit unit) {

    }

    public static class Deserializer extends ValueDeserializer<LocalDateTime> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;

        public Deserializer(ZoneId zid, Offset instantOffset, Offset localDateOffset) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
        }

        @Override
        public LocalDateTime deserialize(JsonParser jp, DeserializationContext dc) {
            return Instant.parse(jp.getString())
                    .minus(instantOffset.amount(), instantOffset.unit())
                    .atZone(zid)
                    .toLocalDateTime()
                    .minus(localDateOffset.amount(), localDateOffset.unit());
        }

        @Override
        public LocalDateTime getNullValue(DeserializationContext ctxt) {
            return null;
        }

    }

    public static class Serializer extends ValueSerializer<LocalDateTime> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;

        public Serializer(ZoneId zid, Offset instantOffset, Offset localDateOffset) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
        }
        
        
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            final var asIsoInstant = value
                    .plus(localDateOffset.amount(), localDateOffset.unit())
                    .atZone(zid)
                    .toInstant()
                    .plus(instantOffset.amount(), instantOffset.unit())
                    .toString();

            gen.writeString(asIsoInstant);
        }
    }

}
