package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.DateTimeException;
import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;

public class EarlyChecksTest {

    public record BrokenTrimBean(@Quirks.Trim Integer invalidTarget) {}

    public record BrokenBoolBean(@Quirks.Bool String invalidTarget) {}

    public record BrokenLocalDateBean(@Quirks.LocalDateAsIsoInstant String invalidTarget) {}

    public record BrokenLocalDateTimeBean(@Quirks.LocalDateTimeAsIsoInstant LocalDate invalidTarget) {}

    public record BrokenTemporalFormatBean(@Quirks.TemporalFormat("yyyy") String invalidTarget) {}

    public record UnknownZoneBean(@Quirks.LocalDateAsIsoInstant("Not/AZone") LocalDate value) {}

    public record InvalidPatternBean(@Quirks.TemporalFormat("yyyy-jj") LocalDate value) {}

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(Quirks.defaults().build())
            .build();

    @Test
    public void shouldCrashOnInvalidTrimPlacement() {
        final var exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.writerFor(BrokenTrimBean.class);
        }, "@Trim on a non-String property is rejected when the serializer is built");

        Assertions.assertTrue(exception.getMessage().contains("Can only be applied to String properties"), "the failure explains the valid placement");
    }

    @Test
    public void shouldCrashOnInvalidTrimPlacementWhenDeserializing() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.readerFor(BrokenTrimBean.class);
        }, "@Trim on a non-String property is rejected when the deserializer is built");
    }

    @Test
    public void shouldCrashOnInvalidBoolPlacement() {
        final var exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.readerFor(BrokenBoolBean.class);
        }, "@Bool on a non-boolean property is rejected when the deserializer is built");

        Assertions.assertTrue(exception.getMessage().contains("Can only be applied to boolean/Boolean fields"), "the failure explains the valid placement");
    }

    @Test
    public void shouldCrashOnInvalidBoolPlacementWhenSerializing() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.writerFor(BrokenBoolBean.class);
        }, "@Bool on a non-boolean property is rejected when the serializer is built");
    }

    @Test
    public void shouldCrashOnInvalidLocalDateAsIsoInstantPlacement() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.writerFor(BrokenLocalDateBean.class);
        }, "@LocalDateAsIsoInstant on a non-LocalDate property is rejected when the serializer is built");
        Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.readerFor(BrokenLocalDateBean.class);
        }, "@LocalDateAsIsoInstant on a non-LocalDate property is rejected when the deserializer is built");
    }

    @Test
    public void shouldCrashOnInvalidLocalDateTimeAsIsoInstantPlacement() {
        final var exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.readerFor(BrokenLocalDateTimeBean.class);
        }, "@LocalDateTimeAsIsoInstant on a LocalDate property is rejected when the deserializer is built");
        Assertions.assertTrue(exception.getMessage().contains("Can only be applied to LocalDateTime fields"), "the failure explains the valid placement");
    }

    @Test
    public void shouldCrashOnTemporalFormatOfAnUnsupportedType() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.readerFor(BrokenTemporalFormatBean.class);
        }, "@TemporalFormat on a non-temporal property is rejected when the deserializer is built");
    }

    @Test
    public void shouldCrashOnAnUnknownZone() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            mapper.readerFor(UnknownZoneBean.class);
        }, "an unknown zone id is rejected when the deserializer is built");
    }

    @Test
    public void shouldCrashOnAnInvalidPattern() {
        Assertions.assertThrows(InvalidDefinitionException.class, () -> {
            mapper.writeValueAsString(new InvalidPatternBean(LocalDate.EPOCH));
        }, "an invalid pattern fails the serialization of the type");
        Assertions.assertThrows(InvalidDefinitionException.class, () -> {
            mapper.readValue("{}", InvalidPatternBean.class);
        }, "an invalid pattern fails the deserialization of the type");
    }
}
