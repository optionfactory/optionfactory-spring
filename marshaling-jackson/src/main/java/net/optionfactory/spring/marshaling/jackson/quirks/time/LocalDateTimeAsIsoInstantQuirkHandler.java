package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

public class LocalDateTimeAsIsoInstantQuirkHandler extends AbstractTemporalAsIsoInstantQuirkHandler<Quirks.LocalDateTimeAsIsoInstant, LocalDateTime> {

    @Override
    public Class<Quirks.LocalDateTimeAsIsoInstant> annotation() {
        return Quirks.LocalDateTimeAsIsoInstant.class;
    }

    @Override
    protected Class<LocalDateTime> targetType() {
        return LocalDateTime.class;
    }

    @Override
    protected String annotationLabel() {
        return "LocalDateTimeAsIsoInstant";
    }

    @Override
    protected ZonedDateTime toZoned(LocalDateTime value, ZoneId zid, Offset ldo) {
        return value.plus(ldo.amount(), ldo.unit()).atZone(zid);
    }

    @Override
    protected LocalDateTime fromZoned(ZonedDateTime zdt, Offset ldo) {
        return zdt.toLocalDateTime().minus(ldo.amount(), ldo.unit());
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.LocalDateTimeAsIsoInstant ann, BeanPropertyWriter bpw) {
        return configureSerialization(bpw, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.LocalDateTimeAsIsoInstant ann, SettableBeanProperty sbp) {
        return configureDeserialization(sbp, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }
}
