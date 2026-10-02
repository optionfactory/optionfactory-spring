package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.HashMap;
import java.util.Map;
import java.time.format.DateTimeParseException;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.TemporalFormat;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

/// Handles [Quirks.TemporalFormat]: see the annotation for the supported types and failure modes.
public class TemporalFormatQuirkHandler implements QuirkHandler<Quirks.TemporalFormat> {

    private static final Map<Class<?>, TemporalQuery<?>> TEMPORAL_TYPE_TO_QUERY = new HashMap<>();

    static {
        TEMPORAL_TYPE_TO_QUERY.put(LocalDate.class, LocalDate::from);
        TEMPORAL_TYPE_TO_QUERY.put(LocalDateTime.class, LocalDateTime::from);
        TEMPORAL_TYPE_TO_QUERY.put(LocalTime.class, LocalTime::from);
        TEMPORAL_TYPE_TO_QUERY.put(Instant.class, Instant::from);
        TEMPORAL_TYPE_TO_QUERY.put(OffsetDateTime.class, OffsetDateTime::from);
        TEMPORAL_TYPE_TO_QUERY.put(OffsetTime.class, OffsetTime::from);
        TEMPORAL_TYPE_TO_QUERY.put(ZonedDateTime.class, ZonedDateTime::from);
        TEMPORAL_TYPE_TO_QUERY.put(Year.class, Year::from);
        TEMPORAL_TYPE_TO_QUERY.put(YearMonth.class, YearMonth::from);
        TEMPORAL_TYPE_TO_QUERY.put(MonthDay.class, MonthDay::from);
        TEMPORAL_TYPE_TO_QUERY.put(ZoneOffset.class, ZoneOffset::from);
    }

    /// @return [Quirks.TemporalFormat]
    @Override
    public Class<TemporalFormat> annotation() {
        return Quirks.TemporalFormat.class;
    }

    /// @param ann the annotation, with the pattern
    /// @param bpw the writer of the property
    /// @return the same writer, with a [Serializer] assigned
    /// @throws IllegalArgumentException when the pattern is invalid
    @Override
    public BeanPropertyWriter serialization(TemporalFormat ann, BeanPropertyWriter bpw) {
        final var dtf = DateTimeFormatter.ofPattern(ann.value());
        final var serializer = new Serializer(dtf);
        bpw.assignSerializer(serializer);
        return bpw;
    }

    /// @param ann the annotation, with the pattern
    /// @param sbp the property
    /// @return a copy of the property with a [Deserializer] for its type
    /// @throws IllegalArgumentException when the pattern is invalid
    /// @throws IllegalStateException when the property type is not a supported `java.time` type
    @Override
    public SettableBeanProperty deserialization(TemporalFormat ann, SettableBeanProperty sbp) {
        final var dtf = DateTimeFormatter.ofPattern(ann.value());
        final var raw = sbp.getType().getRawClass();
        final var query = TEMPORAL_TYPE_TO_QUERY.get(raw);
        if (query == null) {
            throw new IllegalStateException("Unsupported temporal type for @TemporalFormat: " + raw.getName());
        }
        final var deserializer = new Deserializer(dtf, query, raw);
        return sbp.withValueDeserializer(deserializer);
    }

    /// Writes a temporal value as a string through a formatter.
    public static class Serializer extends ValueSerializer<Object> {

        private final DateTimeFormatter dtf;

        /// @param dtf the formatter
        public Serializer(DateTimeFormatter dtf) {
            this.dtf = dtf;
        }

        /// @param value the value, which must be a `TemporalAccessor` carrying the fields the
        /// formatter prints
        /// @param gen the generator
        /// @param ctxt the serialization context
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            gen.writeString(dtf.format((TemporalAccessor) value));
        }
    }

    /// Reads a temporal value from a string through a formatter.
    public static class Deserializer extends ValueDeserializer<Object> {

        private final DateTimeFormatter dtf;
        private final TemporalQuery<?> query;
        private final Class<?> targetType;

        /// @param dtf the formatter
        /// @param query the query extracting the target type from the parsed fields, e.g.
        /// `LocalDate::from`
        /// @param targetType the target type, named in the failures
        public Deserializer(DateTimeFormatter dtf, TemporalQuery<?> query, Class<?> targetType) {
            this.dtf = dtf;
            this.query = query;
            this.targetType = targetType;
        }

        /// @param jp the parser, on the value token
        /// @param dc the deserialization context
        /// @return the parsed value
        /// @throws tools.jackson.databind.exc.MismatchedInputException for a token that is not a
        /// string, and for a blank string or one the formatter cannot parse into the target type
        @Override
        public Object deserialize(JsonParser jp, DeserializationContext dc) {
            if (!jp.hasToken(JsonToken.VALUE_STRING)) {
                return dc.reportInputMismatch(targetType, "Expected a string text token for @TemporalFormat field, got: %s", jp.currentToken());
            }
            final String text = jp.getValueAsString();
            if (text == null || text.isBlank()) {
                return dc.reportInputMismatch(targetType, "Blank text for temporal field.");
            }
            try {
                return dtf.parse(text, query);
            } catch (DateTimeParseException e) {
                return dc.reportInputMismatch(targetType, "Text '%s' could not be parsed against pattern '%s'", text, dtf.toString());
            }
        }

        /// @param ctxt the deserialization context
        /// @return `null`
        @Override
        public TemporalAccessor getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
