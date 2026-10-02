package net.optionfactory.spring.marshaling.jackson.quirks;

import net.optionfactory.spring.marshaling.jackson.quirks.Quirks.Scream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class ScreamTest {

    public record Bean(@Scream String assignedValue) {

    }

    @Test
    public void canSerializeWithoutQuirksModule() {
        final var om = JsonMapper.builder().build();
        final var got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"assignedValue":"a"}
        """;
        Assertions.assertEquals(expected.trim(), got, "without the module the java name is used");
    }

    @Test
    public void canDeserializeWithoutQuirksModule(){
        final var om = JsonMapper.builder().build();
        final var source = """
        {"assignedValue":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assertions.assertEquals(new Bean("a"), got, "without the module the java name is read");
    }

    @Test
    public void canSerialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        String got = om.writeValueAsString(new Bean("a"));
        final var expected = """
        {"ASSIGNED_VALUE":"a"}
        """;
        Assertions.assertEquals(expected.trim(), got, "the camelCase name is written in SCREAMING_SNAKE_CASE");
    }

    @Test
    public void canDeserialize() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var source = """
        {"ASSIGNED_VALUE":"a"}
        """;
        final var got = om.readValue(source, Bean.class);
        Assertions.assertEquals(new Bean("a"), got, "the property is read from the SCREAMING_SNAKE_CASE name");
    }

    public record AcronymBean(@Scream String userID) {

    }

    @Test
    public void acronymsAreSplitLetterByLetter() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var json = om.writeValueAsString(new AcronymBean("a"));
        Assertions.assertEquals("""
        {"USER_I_D":"a"}
        """.trim(), json, "every uppercase letter starts a new word");
        Assertions.assertEquals(new AcronymBean("a"), om.readValue(json, AcronymBean.class), "the same name is read back");
    }
}
