package net.optionfactory.spring.marshaling.jackson.quirks.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;

/// Handles [Quirks.LocalDateTimeAsIsoInstant]: see the annotation for the representation.
///
/// A local date-time falling in a daylight saving gap of the zone is shifted forward, as
/// `LocalDateTime.atZone` does.
public class LocalDateTimeAsIsoInstantQuirkHandler extends AbstractTemporalAsIsoInstantQuirkHandler<Quirks.LocalDateTimeAsIsoInstant, LocalDateTime> {

    /// @return [Quirks.LocalDateTimeAsIsoInstant]
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

    /// @param ann the annotation, with the zone and offsets
    /// @param bpw the writer of the property
    /// @return the same writer, with a serializer assigned
    /// @throws IllegalStateException when the property is not a `LocalDateTime`
    /// @throws java.time.DateTimeException when the zone is unknown
    @Override
    public BeanPropertyWriter serialization(Quirks.LocalDateTimeAsIsoInstant ann, BeanPropertyWriter bpw) {
        return configureSerialization(bpw, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }

    /// @param ann the annotation, with the zone and offsets
    /// @param sbp the property
    /// @return a copy of the property with a deserializer
    /// @throws IllegalStateException when the property is not a `LocalDateTime`
    /// @throws java.time.DateTimeException when the zone is unknown
    @Override
    public SettableBeanProperty deserialization(Quirks.LocalDateTimeAsIsoInstant ann, SettableBeanProperty sbp) {
        return configureDeserialization(sbp, ann.value(), new Offset(ann.ioffset(), ann.iunit()), new Offset(ann.ldoffset(), ann.ldunit()));
    }
}
