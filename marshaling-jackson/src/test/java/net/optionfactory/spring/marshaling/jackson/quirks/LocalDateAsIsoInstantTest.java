package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.LocalDate;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.LocalDateAsIsoInstant;
import org.junit.Assert;
import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;

public class LocalDateAsIsoInstantTest {

    public record Bean(@LocalDateAsIsoInstant LocalDate value) {

    }

    @Test
    public void canSerializeWithoutQuirksModule() {
        final var om = new JsonMapper();
        //om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String got = om.writeValueAsString(new Bean(LocalDate.parse("2024-01-02")));

        Assert.assertEquals("""
                            {"value":"2024-01-02"}
                            """.trim(), got);
    }

    @Test
    public void canSerialize()  {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();

        String got = om.writeValueAsString(new Bean(LocalDate.parse("2024-01-02")));

        Assert.assertEquals("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """.trim(), got);
    }

    @Test
    public void canDeserializeWithoutQuirksModule() {
        final var om = new JsonMapper();

        final var got = om.readValue("""
                            {"value":"2024-01-02"}
                            """, Bean.class);

        Assert.assertEquals(new Bean(LocalDate.parse("2024-01-02")), got);
    }

    @Test
    public void canDeserialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();

        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """, Bean.class);

        Assert.assertEquals(new Bean(LocalDate.parse("2024-01-02")), got);
    }
}
