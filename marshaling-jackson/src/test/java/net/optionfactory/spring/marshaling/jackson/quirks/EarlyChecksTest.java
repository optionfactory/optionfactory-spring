package net.optionfactory.spring.marshaling.jackson.quirks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class EarlyChecksTest {

    public record BrokenTrimBean(@Quirks.Trim Integer invalidTarget) {}

    public record BrokenBoolBean(@Quirks.Bool String invalidTarget) {}

    @Test
    public void shouldCrashOnInvalidTrimPlacement() {
        final var mapper = JsonMapper.builder()
                .addModule(Quirks.defaults().build())
                .build();

        final var exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.writerFor(BrokenTrimBean.class);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("Can only be applied to String properties"));
    }

    @Test
    public void shouldCrashOnInvalidBoolPlacement() {
        final var mapper = JsonMapper.builder()
                .addModule(Quirks.defaults().build())
                .build();

        final var exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            mapper.readerFor(BrokenBoolBean.class);
        });
        
        Assertions.assertTrue(exception.getMessage().contains("Can only be applied to boolean/Boolean fields"));
    }
}