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
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.TemporalFormat;

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

    @Override
    public Class<TemporalFormat> annotation() {
        return Quirks.TemporalFormat.class;
    }

    @Override
    public BeanPropertyWriter serialization(TemporalFormat ann, BeanPropertyWriter bpw) {
        final var dtf = DateTimeFormatter.ofPattern(ann.value());
        final var serializer = new Serializer(dtf);
        bpw.assignSerializer((JsonSerializer) serializer);
        return bpw;
    }

    @Override
    public SettableBeanProperty deserialization(TemporalFormat ann, SettableBeanProperty sbp) {
        final var dtf = DateTimeFormatter.ofPattern(ann.value());
        final var raw = sbp.getType().getRawClass();

        final var deserializer = new Deserializer(dtf, TEMPORAL_TYPE_TO_QUERY.get(raw));
        return sbp.withValueDeserializer(deserializer);
    }

    public static class Serializer extends JsonSerializer<TemporalAccessor> {

        private final DateTimeFormatter dtf;

        public Serializer(DateTimeFormatter dtf) {
            this.dtf = dtf;
        }

        @Override
        public void serialize(TemporalAccessor t, JsonGenerator jg, SerializerProvider sp) throws IOException {
            jg.writeString(dtf.format(t));
        }

    }

    public static class Deserializer extends JsonDeserializer<Object> {

        private final DateTimeFormatter dtf;
        private final TemporalQuery<?> query;

        public Deserializer(DateTimeFormatter dtf, TemporalQuery<?> query) {
            this.dtf = dtf;
            this.query = query;
        }

        @Override
        public Object deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JacksonException {
            return dtf.parse(jp.getText(), query);
        }

        @Override
        public TemporalAccessor getNullValue(DeserializationContext ctxt) throws JsonMappingException {
            return null;
        }

    }

}
