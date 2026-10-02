package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class TimestampTest {

    public record DefaultBean(@Quirks.Timestamp Instant value) {

    }

    public record SecondsBean(@Quirks.Timestamp(millis = false) Instant value) {

    }

    private final JsonMapper om = JsonMapper.builder()
            .addModule(Quirks.defaults().build())
            .build();

    private final Instant testInstant = Instant.parse("2026-07-17T22:00:00Z");

    @Test
    public void canSerializeMillisByDefault() {
        final var bean = new DefaultBean(testInstant);
        final String got = om.writeValueAsString(bean);

        Assertions.assertEquals("""
                {"value":1784325600000}
                """.trim(), got.trim(), "milliseconds since the epoch by default");
    }

    @Test
    public void canDeserializeMillisByDefault() {
        final String json = """
                {"value":1784325600000}
                """;
        final var got = om.readValue(json, DefaultBean.class);

        Assertions.assertEquals(new DefaultBean(testInstant), got, "an integer is read as milliseconds by default");
    }

    @Test
    public void canDeserializeStringifiedMillisBecuaseOfCoercion() {
        final String json = """
                {"value":"1784325600000"}
                """;
        final var got = om.readValue(json, DefaultBean.class);

        Assertions.assertEquals(new DefaultBean(testInstant), got, "a string holding an integer is accepted too");
    }

    @Test
    public void canSerializeSecondsWhenConfigured() {
        final var bean = new SecondsBean(testInstant);
        final String got = om.writeValueAsString(bean);

        Assertions.assertEquals("""
                {"value":1784325600}
                """.trim(), got.trim(), "seconds since the epoch when millis = false");
    }

    @Test
    public void canDeserializeSecondsWhenConfigured() {
        final String json = """
                {"value":1784325600}
                """;
        final var got = om.readValue(json, SecondsBean.class);

        Assertions.assertEquals(new SecondsBean(testInstant), got, "an integer is read as seconds when millis = false");
    }

    @Test
    public void serializesNullValuesGracefully() {
        final var bean = new DefaultBean(null);
        final String got = om.writeValueAsString(bean);

        Assertions.assertEquals("""
                {"value":null}
                """.trim(), got.trim(), "a null instant is written as json null");
    }

    @Test
    public void deserializesNullValuesGracefully() {
        final String json = """
                {"value":null}
                """;
        final var got = om.readValue(json, DefaultBean.class);

        Assertions.assertEquals(new DefaultBean(null), got, "a json null is read as null");
    }

    @Test
    public void rejectsMalformedNumericStringWithException() {
        final String json = """
                {"value":"1784325600garbage"}
                """;
        Assertions.assertThrows(tools.jackson.databind.exc.MismatchedInputException.class, () -> {
            om.readValue(json, DefaultBean.class);
        }, "Should throw MismatchedInputException for non-numeric content inside strings");
    }

    @Test
    public void rejectsInvalidTokenStructuresWithException() {
        final String json = """
                {"value": { "nested": 12345 } }
                """;
        Assertions.assertThrows(tools.jackson.databind.exc.MismatchedInputException.class, () -> {
            om.readValue(json, DefaultBean.class);
        }, "Should throw MismatchedInputException when receiving an Object token instead of number/string");
    }

    @Test
    public void serializingSecondsDropsTheSubSecondPart() {
        final String got = om.writeValueAsString(new SecondsBean(Instant.ofEpochMilli(1999)));
        Assertions.assertEquals("""
                {"value":1}
                """.trim(), got, "the precision seconds cannot carry is dropped");
    }

    @Test
    public void acceptsNumericStringsSurroundedByWhitespace() {
        final var got = om.readValue("""
                {"value":" 1784325600000 "}
                """, DefaultBean.class);
        Assertions.assertEquals(new DefaultBean(testInstant), got, "the numeric string is trimmed before parsing");
    }

    @Test
    public void rejectsBlankStrings() {
        Assertions.assertThrows(tools.jackson.databind.exc.MismatchedInputException.class, () -> {
            om.readValue("""
                {"value":" "}
                """, DefaultBean.class);
        }, "a blank string holds no timestamp");
    }

    @Test
    public void rejectsDecimalNumbers() {
        Assertions.assertThrows(tools.jackson.databind.exc.MismatchedInputException.class, () -> {
            om.readValue("""
                {"value":1784325600000.5}
                """, DefaultBean.class);
        }, "a timestamp must be an integer");
    }
}
