package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

/// Handles [Quirks.LocalDateAsIsoInstant]: see the annotation for the representation.
///
/// The local date is placed at the start of its day in the zone; when deserializing, the time of
/// day the instant has in the zone is dropped.
public class LocalDateAsIsoInstantQuirkHandler extends AbstractTemporalAsIsoInstantQuirkHandler<Quirks.LocalDateAsIsoInstant, LocalDate> {

    /// @return [Quirks.LocalDateAsIsoInstant]
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

    /// @param ann the annotation, with the zone and offsets
    /// @param bpw the writer of the property
    /// @return the same writer, with a serializer assigned
    /// @throws IllegalStateException when the property is not a `LocalDate`
    /// @throws java.time.DateTimeException when the zone is unknown
    @Override
    public BeanPropertyWriter serialization(Quirks.LocalDateAsIsoInstant ann, BeanPropertyWriter bpw) {
        return configureSerialization(bpw, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }

    /// @param ann the annotation, with the zone and offsets
    /// @param sbp the property
    /// @return a copy of the property with a deserializer
    /// @throws IllegalStateException when the property is not a `LocalDate`
    /// @throws java.time.DateTimeException when the zone is unknown
    @Override
    public SettableBeanProperty deserialization(Quirks.LocalDateAsIsoInstant ann, SettableBeanProperty sbp) {
        return configureDeserialization(sbp, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }
}
