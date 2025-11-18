package net.optionfactory.spring.marshaling.jackson.quirks;

import org.junit.Assert;
import org.junit.Test;
import tools.jackson.databind.json.JsonMapper;

public class RenameTest {

    public record Bean(@Quirks.Rename("renamed") String originalName) {

    }

    @Test
    public void canSerializeWithoutQuirksModule() {
        final var om = new JsonMapper();
        final var got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"originalName":"a"}
        """;
        Assert.assertEquals(expected.trim(), got);
    }

    @Test
    public void canDeserializeWithoutQuirksModule() {
        final var om = new JsonMapper();
        final var source = """
        {"originalName":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assert.assertEquals(new Bean("a"), got);
    }

    @Test
    public void canSerialize()  {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        
        String got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"renamed":"a"}
        """;
        Assert.assertEquals(expected.trim(), got);
    }

    @Test
    public void canDeserialize()  {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var source = """
        {"renamed":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assert.assertEquals(new Bean("a"), got);
    }
}
