package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

public abstract class AbstractTemporalAsIsoInstantQuirkHandler<A extends Annotation, T> implements QuirkHandler<A> {

    public record Offset(int amount, ChronoUnit unit) {
    }

    protected abstract Class<T> targetType();

    protected abstract String annotationLabel();

    protected abstract ZonedDateTime toZoned(T value, ZoneId zid, Offset ldo);

    protected abstract T fromZoned(ZonedDateTime zdt, Offset ldo);

    protected final BeanPropertyWriter configureSerialization(BeanPropertyWriter bpw, String zoneId, Offset io, Offset ldo) {
        final Class<?> raw = bpw.getType().getRawClass();
        if (raw != targetType()) {
            throw new IllegalStateException(String.format(
                    "Invalid @%s placement on property '%s'. Can only be applied to %s fields, but found type: %s",
                    annotationLabel(), bpw.getName(), targetType().getSimpleName(), raw.getName()));
        }
        bpw.assignSerializer(new Serializer(ZoneId.of(zoneId), io, ldo));
        return bpw;
    }

    protected final SettableBeanProperty configureDeserialization(SettableBeanProperty sbp, String zoneId, Offset io, Offset ldo) {
        final Class<?> raw = sbp.getType().getRawClass();
        if (raw != targetType()) {
            throw new IllegalStateException(String.format(
                    "Invalid @%s placement on property '%s'. Can only be applied to %s fields, but found type: %s",
                    annotationLabel(), sbp.getName(), targetType().getSimpleName(), raw.getName()));
        }
        return sbp.withValueDeserializer(new Deserializer(ZoneId.of(zoneId), io, ldo));
    }

    public class Serializer extends ValueSerializer<Object> {

        private final ZoneId zid;
        private final Offset io;
        private final Offset ldo;

        public Serializer(ZoneId zid, Offset io, Offset ldo) {
            this.zid = zid;
            this.io = io;
            this.ldo = ldo;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
            final T cast = targetType().cast(value);
            final var asIsoInstant = toZoned(cast, zid, ldo).toInstant().plus(io.amount(), io.unit()).toString();
            gen.writeString(asIsoInstant);
        }
    }

    public class Deserializer extends ValueDeserializer<T> {

        private final ZoneId zid;
        private final Offset io;
        private final Offset ldo;

        public Deserializer(ZoneId zid, Offset io, Offset ldo) {
            this.zid = zid;
            this.io = io;
            this.ldo = ldo;
        }

        @Override
        public T deserialize(JsonParser jp, DeserializationContext dc) {
            if (!jp.hasToken(JsonToken.VALUE_STRING)) {
                return dc.reportInputMismatch(targetType(), "Expected a string token representing an ISO instant, but got: %s", jp.currentToken());
            }
            final String text = jp.getValueAsString();
            if (text.isBlank()) {
                return dc.reportInputMismatch(targetType(), "Blank text provided for ISO instant property.");
            }
            try {
                final ZonedDateTime zdt = Instant.parse(text).minus(io.amount(), io.unit()).atZone(zid);
                return fromZoned(zdt, ldo);
            } catch (Exception e) {
                return dc.reportInputMismatch(targetType(), "Text '%s' could not be parsed into a valid ISO Instant.", text);
            }
        }

        @Override
        public T getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
