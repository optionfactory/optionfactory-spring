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
                """.trim(), got.trim());
    }

    @Test
    public void canDeserializeMillisByDefault() {
        final String json = """
                {"value":1784325600000}
                """;
        final var got = om.readValue(json, DefaultBean.class);

        Assertions.assertEquals(new DefaultBean(testInstant), got);
    }

    @Test
    public void canDeserializeStringifiedMillisBecuaseOfCoercion() {
        final String json = """
                {"value":"1784325600000"}
                """;
        final var got = om.readValue(json, DefaultBean.class);

        Assertions.assertEquals(new DefaultBean(testInstant), got);
    }

    @Test
    public void canSerializeSecondsWhenConfigured() {
        final var bean = new SecondsBean(testInstant);
        final String got = om.writeValueAsString(bean);

        Assertions.assertEquals("""
                {"value":1784325600}
                """.trim(), got.trim());
    }

    @Test
    public void canDeserializeSecondsWhenConfigured() {
        final String json = """
                {"value":1784325600}
                """;
        final var got = om.readValue(json, SecondsBean.class);

        Assertions.assertEquals(new SecondsBean(testInstant), got);
    }

    @Test
    public void serializesNullValuesGracefully() {
        final var bean = new DefaultBean(null);
        final String got = om.writeValueAsString(bean);

        Assertions.assertEquals("""
                {"value":null}
                """.trim(), got.trim());
    }

    @Test
    public void deserializesNullValuesGracefully() {
        final String json = """
                {"value":null}
                """;
        final var got = om.readValue(json, DefaultBean.class);

        Assertions.assertEquals(new DefaultBean(null), got);
    }
}
