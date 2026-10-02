package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.LocalDateTime;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.LocalDateTimeAsIsoInstant;
import java.time.temporal.ChronoUnit;
import tools.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class LocalDateTimeAsIsoInstantTest {

    public record Bean(@LocalDateTimeAsIsoInstant LocalDateTime value) {

    }

    @Test
    public void canSerializeWithoutQuirksModule(){
        final var om = JsonMapper.builder().build();
        String got = om.writeValueAsString(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")));

        Assertions.assertEquals("""
                            {"value":"2024-01-02T00:00:00"}
                            """.trim(), got, "without the module the date-time is written as an ISO local date-time");
    }

    @Test
    public void canSerialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        
        String got = om.writeValueAsString(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")));

        Assertions.assertEquals("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """.trim(), got, "the date-time in UTC by default");
    }

    @Test
    public void canDeserializeWithoutQuirksModule()  {
        final var om = new JsonMapper();
        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00"}
                            """, Bean.class);

        Assertions.assertEquals(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")), got, "without the module the date-time is read as an ISO local date-time");
    }

    @Test
    public void canDeserialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """, Bean.class);

        Assertions.assertEquals(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")), got, "the date-time of the instant in UTC by default");
    }

    public record RomeBean(@LocalDateTimeAsIsoInstant(value = "Europe/Rome", ioffset = 30, iunit = ChronoUnit.MINUTES) LocalDateTime value) {

    }

    private final JsonMapper om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();

    @Test
    public void zoneAndOffsetsApplyBothWays() {
        final var value = new RomeBean(LocalDateTime.parse("2024-01-02T10:00:00"));
        final var json = om.writeValueAsString(value);
        Assertions.assertEquals("""
                            {"value":"2024-01-02T09:30:00Z"}
                            """.trim(), json, "10:00 in Rome is 09:00 UTC in winter, plus the 30 minutes of the instant offset");
        Assertions.assertEquals(value, om.readValue(json, RomeBean.class), "the zone and offset are reverted when deserializing");
    }

    @Test
    public void rejectsNonStringTokens() {
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                            {"value":true}
                            """, Bean.class), "a boolean is not an ISO instant");
    }
}
