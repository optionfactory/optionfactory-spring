package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

public class LocalDateAsIsoInstantQuirkHandler extends AbstractTemporalAsIsoInstantQuirkHandler<Quirks.LocalDateAsIsoInstant, LocalDate> {

    @Override
    public Class<Quirks.LocalDateAsIsoInstant> annotation() {
        return Quirks.LocalDateAsIsoInstant.class;
    }

    @Override
    protected Class<LocalDate> targetType() {
        return LocalDate.class;
    }

    @Override
    protected String annotationLabel() {
        return "LocalDateAsIsoInstant";
    }

    @Override
    protected ZonedDateTime toZoned(LocalDate value, ZoneId zid, Offset ldo) {
        return value.plus(ldo.amount(), ldo.unit()).atStartOfDay(zid);
    }

    @Override
    protected LocalDate fromZoned(ZonedDateTime zdt, Offset ldo) {
        return zdt.toLocalDate().minus(ldo.amount(), ldo.unit());
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.LocalDateAsIsoInstant ann, BeanPropertyWriter bpw) {
        return configureSerialization(bpw, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.LocalDateAsIsoInstant ann, SettableBeanProperty sbp) {
        return configureDeserialization(sbp, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }
}
