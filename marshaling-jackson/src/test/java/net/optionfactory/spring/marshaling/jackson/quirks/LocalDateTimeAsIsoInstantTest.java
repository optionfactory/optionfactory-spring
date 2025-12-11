package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.LocalDateTime;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.LocalDateTimeAsIsoInstant;
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
                            """.trim(), got);
    }

    @Test
    public void canSerialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        
        String got = om.writeValueAsString(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")));

        Assertions.assertEquals("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """.trim(), got);
    }

    @Test
    public void canDeserializeWithoutQuirksModule()  {
        final var om = new JsonMapper();
        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00"}
                            """, Bean.class);

        Assertions.assertEquals(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")), got);
    }

    @Test
    public void canDeserialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var got = om.readValue("""
                            {"value":"2024-01-02T00:00:00Z"}
                            """, Bean.class);

        Assertions.assertEquals(new Bean(LocalDateTime.parse("2024-01-02T00:00:00")), got);
    }
}
