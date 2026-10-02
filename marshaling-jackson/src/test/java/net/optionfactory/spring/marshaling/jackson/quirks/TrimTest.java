package net.optionfactory.spring.marshaling.jackson.quirks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

public class TrimTest {

    public record Bean(@Quirks.Trim String value) {

    }

    private final JsonMapper om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();

    @Test
    public void isInertWithoutQuirksModule() {
        final var plain = JsonMapper.builder().build();
        Assertions.assertEquals("""
                {"value":" a "}
                """.trim(), plain.writeValueAsString(new Bean(" a ")), "without the module the value is written as is");
    }

    @Test
    public void canSerialize() {
        Assertions.assertEquals("""
                {"value":"a b"}
                """.trim(), om.writeValueAsString(new Bean(" \ta b\n ")), "leading and trailing whitespace is stripped, inner whitespace kept");
    }

    @Test
    public void canDeserialize() {
        final var got = om.readValue("""
                {"value":" \\ta b\\n "}
                """, Bean.class);
        Assertions.assertEquals(new Bean("a b"), got, "leading and trailing whitespace is stripped, inner whitespace kept");
    }

    @Test
    public void blankStringsBecomeEmpty() {
        final var got = om.readValue("""
                {"value":"   "}
                """, Bean.class);
        Assertions.assertEquals(new Bean(""), got, "a blank string is trimmed to empty, not to null");
    }

    @Test
    public void nullsStayNull() {
        Assertions.assertEquals(new Bean(null), om.readValue("""
                {"value":null}
                """, Bean.class), "a json null deserializes to null");
        Assertions.assertEquals("""
                {"value":null}
                """.trim(), om.writeValueAsString(new Bean(null)), "a null serializes as json null");
    }

    @Test
    public void rejectsNonStringTokens() {
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                {"value":12}
                """, Bean.class), "a number is not coerced to a string");
        Assertions.assertThrows(MismatchedInputException.class, () -> om.readValue("""
                {"value":true}
                """, Bean.class), "a boolean is not coerced to a string");
    }
}
