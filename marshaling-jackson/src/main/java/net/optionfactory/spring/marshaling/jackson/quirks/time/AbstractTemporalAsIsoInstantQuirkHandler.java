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

/// Base of the handlers representing a local temporal value as the ISO instant it identifies in a
/// time zone, shifted by two offsets: one applied to the local value, one to the instant. See
/// [net.optionfactory.spring.marshaling.jackson.quirks.Quirks.LocalDateAsIsoInstant] for the
/// algorithm and failure modes.
///
/// A subclass binds the annotation to a local type, and converts between that type and a
/// `ZonedDateTime`.
///
/// @param <A> the annotation type
/// @param <T> the local temporal type
public abstract class AbstractTemporalAsIsoInstantQuirkHandler<A extends Annotation, T> implements QuirkHandler<A> {

    /// A temporal shift.
    ///
    /// @param amount the amount of units, possibly negative
    /// @param unit the unit, which the shifted type must support
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

    /// Writes a local value as an ISO instant.
    public class Serializer extends ValueSerializer<Object> {

        private final ZoneId zid;
        private final Offset io;
        private final Offset ldo;

        /// @param zid the zone the local value is placed in
        /// @param io the offset added to the instant
        /// @param ldo the offset added to the local value
        public Serializer(ZoneId zid, Offset io, Offset ldo) {
            this.zid = zid;
            this.io = io;
            this.ldo = ldo;
        }

        /// @param value the local value, of the handler's type
        /// @param gen the generator
        /// @param ctxt the serialization context
        /// @throws java.time.temporal.UnsupportedTemporalTypeException when an offset unit is not
        /// supported by the type it shifts
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
            final T cast = targetType().cast(value);
            final var asIsoInstant = toZoned(cast, zid, ldo).toInstant().plus(io.amount(), io.unit()).toString();
            gen.writeString(asIsoInstant);
        }
    }

    /// Reads a local value from an ISO instant.
    public class Deserializer extends ValueDeserializer<T> {

        private final ZoneId zid;
        private final Offset io;
        private final Offset ldo;

        /// @param zid the zone the instant is viewed in
        /// @param io the offset subtracted from the instant
        /// @param ldo the offset subtracted from the local value
        public Deserializer(ZoneId zid, Offset io, Offset ldo) {
            this.zid = zid;
            this.io = io;
            this.ldo = ldo;
        }

        /// @param jp the parser, on the value token
        /// @param dc the deserialization context
        /// @return the local value
        /// @throws tools.jackson.databind.exc.MismatchedInputException for a token that is not a
        /// string, for a blank string, and for one that is not an ISO instant or cannot be shifted
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

        /// @param ctxt the deserialization context
        /// @return `null`
        @Override
        public T getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
