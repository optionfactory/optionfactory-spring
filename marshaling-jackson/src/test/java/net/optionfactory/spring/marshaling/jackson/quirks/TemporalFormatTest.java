package net.optionfactory.spring.marshaling.jackson.quirks;

import java.time.LocalDate;
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
        Assertions.assertEquals("{\"value\":\"17/07/2026\"}", json.trim());
        final var back = om.readValue(json, Bean.class);
        Assertions.assertEquals(bean, back);
    }

    @Test
    public void rejectsObjectTokenWithMismatchedInputExceptionNotNpe() {
        final String json = """
                {"value": { "nested": 1 } }
                """;
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue(json, Bean.class));
    }
}
