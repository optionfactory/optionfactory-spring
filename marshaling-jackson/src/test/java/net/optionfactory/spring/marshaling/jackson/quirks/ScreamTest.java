package net.optionfactory.spring.marshaling.jackson.quirks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.Scream;
import org.junit.Assert;
import org.junit.Test;

public class ScreamTest {

    public record Bean(@Scream String assignedValue) {

    }

    @Test
    public void canSerializeWithoutQuirksModule() throws JsonProcessingException {
        final var om = new ObjectMapper();
        final var got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"assignedValue":"a"}
        """;
        Assert.assertEquals(expected.trim(), got);
    }

    @Test
    public void canDeserializeWithoutQuirksModule() throws JsonProcessingException {
        final var om = new ObjectMapper();
        final var source = """
        {"assignedValue":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assert.assertEquals(new Bean("a"), got);
    }

    @Test
    public void canSerialize() throws JsonProcessingException {
        final var om = new ObjectMapper();
        om.registerModule(Quirks.defaults().build());
        String got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"ASSIGNED_VALUE":"a"}
        """;
        Assert.assertEquals(expected.trim(), got);
    }

    @Test
    public void canDeserialize() throws JsonProcessingException {
        final var om = new ObjectMapper();
        om.registerModule(Quirks.defaults().build());
        final var source = """
        {"ASSIGNED_VALUE":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assert.assertEquals(new Bean("a"), got);
    }
}
