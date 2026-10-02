package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.LocalDate;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

public class TemporalFormatTest {

    public record Bean(@Quirks.TemporalFormat("dd/MM/yyyy") LocalDate value) {
    }

    private final JsonMapper om = JsonMapper.builder()
            .addModule(Quirks.defaults().build())
            .build();

    @Test
    public void canSerializeAndDeserialize() {
        final var bean = new Bean(LocalDate.of(2026, 7, 17));
        final String json = om.writeValueAsString(bean);
        Assertions.assertEquals("{\"value\":\"17/07/2026\"}", json.trim(), "the value is written in the pattern");
        final var back = om.readValue(json, Bean.class);
        Assertions.assertEquals(bean, back, "the value is read back from the pattern");
    }

    @Test
    public void rejectsObjectTokenWithMismatchedInputExceptionNotNpe() {
        final String json = """
                {"value": { "nested": 1 } }
                """;
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue(json, Bean.class), "an object token is a mismatched input, not a crash");
    }

    public record YearMonthBean(@Quirks.TemporalFormat("MM-yyyy") YearMonth value) {
    }

    public record InstantBean(@Quirks.TemporalFormat("yyyy-MM-dd HH:mm XXX") Instant value) {
    }

    @Test
    public void supportsOtherTemporalTypes() {
        final var json = om.writeValueAsString(new YearMonthBean(YearMonth.of(2026, 7)));
        Assertions.assertEquals("{\"value\":\"07-2026\"}", json, "a YearMonth is written in the pattern");
        Assertions.assertEquals(new YearMonthBean(YearMonth.of(2026, 7)), om.readValue(json, YearMonthBean.class), "a YearMonth is read back from the pattern");
    }

    @Test
    public void canDeserializeAnInstantFromAPatternWithAnOffset() {
        final var got = om.readValue("""
                {"value":"2026-07-17 10:00 +02:00"}
                """, InstantBean.class);
        Assertions.assertEquals(new InstantBean(Instant.parse("2026-07-17T08:00:00Z")), got, "the offset in the text identifies the instant");
    }

    @Test
    public void deserializesNullAsNull() {
        Assertions.assertEquals(new Bean(null), om.readValue("""
                {"value":null}
                """, Bean.class), "a json null deserializes to null");
    }

    @Test
    public void rejectsBlankText() {
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                {"value":" "}
                """, Bean.class), "a blank string is not a date");
    }

    @Test
    public void rejectsTextNotMatchingThePattern() {
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                {"value":"2026-07-17"}
                """, Bean.class), "an ISO date does not match dd/MM/yyyy");
    }
}
