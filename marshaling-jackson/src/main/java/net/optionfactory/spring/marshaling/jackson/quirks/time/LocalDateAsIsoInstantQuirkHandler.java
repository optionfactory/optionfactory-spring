package net.optionfactory.spring.marshaling.jackson.quirks.time;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;

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
        bpw.assignSerializer((JsonSerializer) serializer);
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

    public static class Deserializer extends JsonDeserializer<LocalDate> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;

        public Deserializer(ZoneId zid, Offset instantOffset, Offset localDateOffset) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
        }

        @Override
        public LocalDate deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            return Instant.parse(jp.getText())
                    .minus(instantOffset.amount(), instantOffset.unit())
                    .atZone(zid)
                    .toLocalDate()
                    .minus(localDateOffset.amount(), localDateOffset.unit());
        }

        @Override
        public LocalDate getNullValue(DeserializationContext ctxt) throws JsonMappingException {
            return null;
        }

    }

    public static class Serializer extends JsonSerializer<LocalDate> {

        private final ZoneId zid;
        private final Offset instantOffset;
        private final Offset localDateOffset;

        public Serializer(ZoneId zid, Offset instantOffset, Offset localDateOffset) {
            this.zid = zid;
            this.instantOffset = instantOffset;
            this.localDateOffset = localDateOffset;
        }

        @Override
        public void serialize(LocalDate t, JsonGenerator jg, SerializerProvider sp) throws IOException {
            final var asIsoInstant = t
                    .plus(localDateOffset.amount(), localDateOffset.unit())
                    .atStartOfDay(zid)
                    .toInstant()
                    .plus(instantOffset.amount(), instantOffset.unit())
                    .toString();

            jg.writeString(asIsoInstant);
        }
    }

}
