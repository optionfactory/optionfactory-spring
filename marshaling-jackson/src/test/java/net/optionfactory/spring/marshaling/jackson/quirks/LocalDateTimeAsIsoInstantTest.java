package net.optionfactory.spring.marshaling.jackson.quirks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.LocalDateTimeAsIsoInstant;
import org.junit.Assert;
import org.junit.Test;

public class LocalDateTimeAsIsoInstantTest {

    public record Bean(@LocalDateTimeAsIsoInstant LocalDateTime value) {

    }

    @Test
    public void canSerializeWithoutQuirksModule() throws JsonProcessingException {
        final var om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String got = om.writeValueAsString(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")));

        Assert.assertEquals("""
                            {"value":"2024-01-02T00:00:00"}
                            """.trim(), got);
    }

    @Test
    public void canSerialize() throws JsonProcessingException {
        final var om = new ObjectMapper();
        om.registerModule(Quirks.defaults().build());

        String got = om.writeValueAsString(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")));

        Assert.assertEquals("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """.trim(), got);
    }

    @Test
    public void canDeserializeWithoutQuirksModule() throws JsonProcessingException {
        final var om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());

        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00"}
                            """, Bean.class);

        Assert.assertEquals(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")), got);
    }

    @Test
    public void canDeserialize() throws JsonProcessingException {
        final var om = new ObjectMapper();
        om.registerModule(Quirks.defaults().build());

        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """, Bean.class);

        Assert.assertEquals(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")), got);
    }
}
