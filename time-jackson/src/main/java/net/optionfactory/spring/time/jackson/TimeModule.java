package net.optionfactory.spring.time.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import net.optionfactory.spring.time.jackson.adapters.InstantFromEpochMillis;
import net.optionfactory.spring.time.jackson.adapters.InstantToEpochMillis;
import net.optionfactory.spring.time.jackson.adapters.LocalDateAsIsoString;
import net.optionfactory.spring.time.jackson.adapters.LocalDateFromIsoString;
import net.optionfactory.spring.time.jackson.adapters.LocalDateTimeAsIsoString;
import net.optionfactory.spring.time.jackson.adapters.LocalDateTimeFromIsoString;
import net.optionfactory.spring.time.jackson.adapters.OffsetDateTimeAsIsoString;
import net.optionfactory.spring.time.jackson.adapters.OffsetDateTimeFromIsoString;
import net.optionfactory.spring.time.jackson.adapters.ZonedDateTimeAsIsoString;
import net.optionfactory.spring.time.jackson.adapters.ZonedDateTimeFromIsoString;

public class TimeModule extends Module {

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
        
        ss.addSerializer(Instant.class, new InstantToEpochMillis());
        ds.addDeserializer(Instant.class, new InstantFromEpochMillis());
        
        ss.addSerializer(LocalDate.class, new LocalDateAsIsoString());
        ds.addDeserializer(LocalDate.class, new LocalDateFromIsoString());
        
        ss.addSerializer(LocalDateTime.class, new LocalDateTimeAsIsoString());
        ds.addDeserializer(LocalDateTime.class, new LocalDateTimeFromIsoString());
        
        ss.addSerializer(OffsetDateTime.class, new OffsetDateTimeAsIsoString());
        ds.addDeserializer(OffsetDateTime.class, new OffsetDateTimeFromIsoString());
        
        ss.addSerializer(ZonedDateTime.class, new ZonedDateTimeAsIsoString());
        ds.addDeserializer(ZonedDateTime.class, new ZonedDateTimeFromIsoString());
        
        sc.addSerializers(ss);
        sc.addDeserializers(ds);
    }




}
