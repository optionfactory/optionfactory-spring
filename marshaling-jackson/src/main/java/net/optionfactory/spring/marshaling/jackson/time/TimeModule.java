package net.optionfactory.spring.marshaling.jackson.time;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.optionfactory.spring.marshaling.jackson.time.adapters.InstantFromEpochMillis;
import net.optionfactory.spring.marshaling.jackson.time.adapters.InstantFromIsoInstant;
import net.optionfactory.spring.marshaling.jackson.time.adapters.InstantToEpochMillis;
import net.optionfactory.spring.marshaling.jackson.time.adapters.InstantToIsoInstant;
import net.optionfactory.spring.marshaling.jackson.time.adapters.LocalDateAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.LocalDateFromIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.LocalDateTimeAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.LocalDateTimeFromIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.LocalTimeAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.LocalTimeFromIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.MonthDayAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.MonthDayFromIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.OffsetDateTimeAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.OffsetDateTimeFromIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.YearAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.YearFromIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.YearMonthAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.YearMonthFromIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.ZoneIdAsIdString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.ZoneIdFromIdString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.ZonedDateTimeAsIsoString;
import net.optionfactory.spring.marshaling.jackson.time.adapters.ZonedDateTimeFromIsoString;

public class TimeModule extends Module {

    private final boolean instantAsIsoInstant;

    public TimeModule(boolean instantAsIsoInstant) {
        this.instantAsIsoInstant = instantAsIsoInstant;
    }

    public static TimeModule withInstantsAsTimestamps() {
        return new TimeModule(false);
    }

    public static TimeModule withIsoInstants() {
        return new TimeModule(true);
    }

    @Override
    public String getModuleName() {
        return "time-module";
    }

    @Override
    public Version version() {
        return new Version(1, 0, 0, null, "net.optionfactory.spring", "time-jackson");
    }

    @Override
    public void setupModule(SetupContext sc) {
        final SimpleSerializers ss = new SimpleSerializers();
        final SimpleDeserializers ds = new SimpleDeserializers();
        ss.addSerializer(Instant.class, instantAsIsoInstant ? new InstantToIsoInstant() : new InstantToEpochMillis());
        ds.addDeserializer(Instant.class, instantAsIsoInstant ? new InstantFromIsoInstant() : new InstantFromEpochMillis());
        ss.addSerializer(Year.class, new YearAsIsoString());
        ds.addDeserializer(Year.class, new YearFromIsoString());

        ss.addSerializer(YearMonth.class, new YearMonthAsIsoString());
        ds.addDeserializer(YearMonth.class, new YearMonthFromIsoString());

        ss.addSerializer(MonthDay.class, new MonthDayAsIsoString());
        ds.addDeserializer(MonthDay.class, new MonthDayFromIsoString());

        ss.addSerializer(LocalDate.class, new LocalDateAsIsoString());
        ds.addDeserializer(LocalDate.class, new LocalDateFromIsoString());

        ss.addSerializer(LocalTime.class, new LocalTimeAsIsoString());
        ds.addDeserializer(LocalTime.class, new LocalTimeFromIsoString());

        ss.addSerializer(LocalDateTime.class, new LocalDateTimeAsIsoString());
        ds.addDeserializer(LocalDateTime.class, new LocalDateTimeFromIsoString());

        ss.addSerializer(OffsetDateTime.class, new OffsetDateTimeAsIsoString());
        ds.addDeserializer(OffsetDateTime.class, new OffsetDateTimeFromIsoString());

        ss.addSerializer(ZonedDateTime.class, new ZonedDateTimeAsIsoString());
        ds.addDeserializer(ZonedDateTime.class, new ZonedDateTimeFromIsoString());

        ss.addSerializer(ZoneId.class, new ZoneIdAsIdString());
        ds.addDeserializer(ZoneId.class, new ZoneIdFromIdString());

        sc.addSerializers(ss);
        sc.addDeserializers(ds);
    }

}
