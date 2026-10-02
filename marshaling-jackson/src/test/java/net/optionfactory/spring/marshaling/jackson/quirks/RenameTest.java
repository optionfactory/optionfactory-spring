package net.optionfactory.spring.marshaling.jackson.quirks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class RenameTest {

    public record Bean(@Quirks.Rename("renamed") String originalName) {

    }

    public static class FieldBean {

        @Quirks.Rename("renamed")
        public String originalName;
    }

    @Test
    public void canSerializeWithoutQuirksModule() {
        final var om = new JsonMapper();
        final var got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"originalName":"a"}
        """;
        Assertions.assertEquals(expected.trim(), got, "without the module the java name is used");
    }

    @Test
    public void canDeserializeWithoutQuirksModule() {
        final var om = new JsonMapper();
        final var source = """
        {"originalName":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assertions.assertEquals(new Bean("a"), got, "without the module the java name is read");
    }

    @Test
    public void canSerialize()  {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        
        String got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"renamed":"a"}
        """;
        Assertions.assertEquals(expected.trim(), got, "the property is written under the fixed name");
    }

    @Test
    public void canDeserialize()  {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var source = """
        {"renamed":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assertions.assertEquals(new Bean("a"), got, "the property is read from the fixed name");
    }

    @Test
    public void theOriginalNameOfACreatorPropertyIsNotRead() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var got = om.readValue("""
        {"originalName":"a"}
        """, Bean.class);
        Assertions.assertEquals(new Bean(null), got, "a record component is bound to the fixed name only");
    }

    @Test
    public void aFieldPropertyIsReadFromTheFixedName() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var got = om.readValue("""
        {"renamed":"a"}
        """, FieldBean.class);
        Assertions.assertEquals("a", got.originalName, "a field property is read from the fixed name");
    }

    @Test
    public void theOriginalNameOfAFieldPropertyIsNotRead() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var got = om.readValue("""
        {"originalName":"a"}
        """, FieldBean.class);
        Assertions.assertNull(got.originalName, "a field property is bound to the fixed name only, as a record component is");
    }
}
