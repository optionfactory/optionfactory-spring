package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.LocalDate;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.LocalDateAsIsoInstant;
import java.time.temporal.ChronoUnit;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class LocalDateAsIsoInstantTest {

    public record Bean(@LocalDateAsIsoInstant LocalDate value) {

    }

    @Test
    public void canSerializeWithoutQuirksModule() {
        final var om = new JsonMapper();
        String got = om.writeValueAsString(new Bean(LocalDate.parse("2024-01-02")));

        Assertions.assertEquals("""
                            {"value":"2024-01-02"}
                            """.trim(), got, "without the module the date is written as an ISO date");
    }

    @Test
    public void canSerialize()  {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();

        String got = om.writeValueAsString(new Bean(LocalDate.parse("2024-01-02")));

        Assertions.assertEquals("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """.trim(), got, "the start of the day in UTC by default");
    }

    @Test
    public void canDeserializeWithoutQuirksModule() {
        final var om = new JsonMapper();

        final var got = om.readValue("""
                            {"value":"2024-01-02"}
                            """, Bean.class);

        Assertions.assertEquals(new Bean(LocalDate.parse("2024-01-02")), got, "without the module the date is read as an ISO date");
    }

    @Test
    public void canDeserialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();

        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """, Bean.class);

        Assertions.assertEquals(new Bean(LocalDate.parse("2024-01-02")), got, "the date of the instant in UTC by default");
    }

    public record RomeBean(@LocalDateAsIsoInstant("Europe/Rome") LocalDate value) {

    }

    public record LastSecondOfTheDayBean(@LocalDateAsIsoInstant(ldoffset = 1, ioffset = -1, iunit = ChronoUnit.SECONDS) LocalDate value) {

    }

    public record UnsupportedInstantUnitBean(@LocalDateAsIsoInstant(iunit = ChronoUnit.MONTHS) LocalDate value) {

    }

    public record UnsupportedLocalDateUnitBean(@LocalDateAsIsoInstant(ldunit = ChronoUnit.HOURS) LocalDate value) {

    }

    private final JsonMapper om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();

    @Test
    public void canSerializeTheStartOfTheDayInAZone() {
        final var got = om.writeValueAsString(new RomeBean(LocalDate.parse("2024-01-02")));
        Assertions.assertEquals("""
                            {"value":"2024-01-01T23:00:00Z"}
                            """.trim(), got, "midnight in Rome is 23:00 UTC of the day before, in winter");
    }

    @Test
    public void deserializationDropsTheTimeOfDayInTheZone() {
        final var got = om.readValue("""
                            {"value":"2024-01-02T22:59:59Z"}
                            """, RomeBean.class);
        Assertions.assertEquals(new RomeBean(LocalDate.parse("2024-01-02")), got, "23:59:59 in Rome is still the same day");
    }

    @Test
    public void deserializationAcceptsNumericOffsets() {
        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00+01:00"}
                            """, RomeBean.class);
        Assertions.assertEquals(new RomeBean(LocalDate.parse("2024-01-02")), got, "an instant with a numeric offset identifies the same moment");
    }

    @Test
    public void offsetsShiftBothWays() {
        final var json = om.writeValueAsString(new LastSecondOfTheDayBean(LocalDate.parse("2024-01-02")));
        Assertions.assertEquals("""
                            {"value":"2024-01-02T23:59:59Z"}
                            """.trim(), json, "the start of the next day, minus one second");
        Assertions.assertEquals(new LastSecondOfTheDayBean(LocalDate.parse("2024-01-02")), om.readValue(json, LastSecondOfTheDayBean.class), "the offsets are reverted when deserializing");
    }

    @Test
    public void nullsStayNull() {
        Assertions.assertEquals("""
                            {"value":null}
                            """.trim(), om.writeValueAsString(new Bean(null)), "a null date is written as json null");
        Assertions.assertEquals(new Bean(null), om.readValue("""
                            {"value":null}
                            """, Bean.class), "a json null is read as null");
    }

    @Test
    public void rejectsMalformedInput() {
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                            {"value":1}
                            """, Bean.class), "a number is not an ISO instant");
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                            {"value":""}
                            """, Bean.class), "a blank string is not an ISO instant");
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                            {"value":"2024-01-02"}
                            """, Bean.class), "a date is not an ISO instant");
    }

    @Test
    public void unitsTheShiftedTypeDoesNotSupportFailEveryValue() {
        Assertions.assertThrows(DatabindException.class, () -> om.writeValueAsString(new UnsupportedInstantUnitBean(LocalDate.parse("2024-01-02"))), "an Instant cannot be shifted by months, even by zero of them");
        Assertions.assertThrows(DatabindException.class, () -> om.writeValueAsString(new UnsupportedLocalDateUnitBean(LocalDate.parse("2024-01-02"))), "a LocalDate cannot be shifted by hours, even by zero of them");
    }
}
