package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

public class LocalDateAsIsoInstantQuirkHandler implements QuirkHandler<Quirks.LocalDateAsIsoInstant> {

    @Override
    public Class<Quirks.LocalDateAsIsoInstant> annotation() {
        return Quirks.LocalDateAsIsoInstant.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.LocalDateAsIsoInstant ann, BeanPropertyWriter bpw) {
        final var zoneId = ZoneId.of(ann.value());
        final var instantOffset = new Offset(ann.ioffset(), ann.iunit());
        final var localDateOffset = new Offset(ann.ldoffset(), ann.ldunit());
        final var serializer = new Serializer(zoneId, instantOffset, localDateOffset);
        bpw.assignSerializer((ValueSerializer) serializer);
        return bpw;

    }

    @Override
    public SettableBeanProperty deserialization(Quirks.LocalDateAsIsoInstant ann, SettableBeanProperty sbp) {
        final var zoneId = ZoneId.of(ann.value());
        final var instantOffset = new Offset(ann.ioffset(), ann.iunit());
        final var localDateOffset = new Offset(ann.ldoffset(), ann.ldunit());
        final var deserializer = new Deserializer(zoneId, instantOffset, localDateOffset);
        return sbp.withValueDeserializer(deserializer);
    }

    public record Offset(int amount, ChronoUnit unit) {

    }

    public static class Deserializer extends ValueDeserializer<LocalDate> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;

        public Deserializer(ZoneId zid, Offset instantOffset, Offset localDateOffset) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
        }

        @Override
        public LocalDate deserialize(JsonParser jp, DeserializationContext dc) {
            return Instant.parse(jp.getText())
                    .minus(instantOffset.amount(), instantOffset.unit())
                    .atZone(zid)
                    .toLocalDate()
                    .minus(localDateOffset.amount(), localDateOffset.unit());
        }

        @Override
        public LocalDate getNullValue(DeserializationContext ctxt) {
            return null;
        }

    }

    public static class Serializer extends ValueSerializer<LocalDate> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;

        public Serializer(ZoneId zid, Offset instantOffset, Offset localDateOffset) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
        }
        
        
        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializationContext ctxt) {
            final var asIsoInstant = value
                    .plus(localDateOffset.amount(), localDateOffset.unit())
                    .atStartOfDay(zid)
                    .toInstant()
                    .plus(instantOffset.amount(), instantOffset.unit())
                    .toString();

            gen.writeString(asIsoInstant);
        }
    }

}
