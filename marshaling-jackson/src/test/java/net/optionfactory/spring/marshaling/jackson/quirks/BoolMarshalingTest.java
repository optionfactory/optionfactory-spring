package net.optionfactory.spring.marshaling.jackson.quirks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

public class BoolMarshalingTest {

    public record NullableSiNo(@Quirks.Bool Boolean value) {

    }

    public record NonnullableSiNo(@Quirks.Bool boolean value) {

    }

    private final JsonMapper configuredOm = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
    private final JsonMapper defaultOm = JsonMapper.builder().build();

    public <T> T deser(Class<T> type, boolean featureEnabled, String value) {
        return (featureEnabled ? configuredOm : defaultOm).readValue(value, type);
    }

    public String ser(boolean featureEnabled, Object value) {
        return (featureEnabled ? configuredOm : defaultOm).writeValueAsString(value);
    }

    @Test
    public void deserialization() throws Exception {
        Assertions.assertEquals(
                new NullableSiNo(Boolean.TRUE),
                deser(NullableSiNo.class, true,
                        """
                        {"value": "SI"}
                        """
                ),
                "canDeserializeSiToNullableField"
        );
        Assertions.assertEquals(
                new NullableSiNo(Boolean.TRUE),
                deser(NullableSiNo.class, false,
                        """
                        {"value": true}
                        """
                ),
                "canDeserializeBooleanToNullableField"
        );
        Assertions.assertEquals(
                new NullableSiNo(Boolean.FALSE),
                deser(NullableSiNo.class, true,
                        """
                        {"value": "NO"}
                        """
                ),
                "canDeserializeNoToNullableField"
        );
        Assertions.assertEquals(
                new NullableSiNo(Boolean.FALSE),
                deser(NullableSiNo.class, false,
                        """
                        {"value": false}
                        """
                ),
                "canDeserializeFalseToNullableField"
        );

        Assertions.assertEquals(
                new NullableSiNo(null),
                deser(NullableSiNo.class, true,
                        """
                        {"value": null}
                        """
                ),
                "canDeserializeNullToNullableField"
        );
        Assertions.assertEquals(
                new NullableSiNo(null),
                deser(NullableSiNo.class, false,
                        """
                        {"value": null}
                        """
                ),
                "canDeserializeNullToNullableField"
        );

        Assertions.assertEquals(
                new NonnullableSiNo(true),
                deser(NonnullableSiNo.class, true,
                        """
                        {"value": "SI"}
                        """
                ),
                "canDeserializeSiToNonnullableField"
        );
        Assertions.assertEquals(
                new NonnullableSiNo(true),
                deser(NonnullableSiNo.class, false,
                        """
                        {"value": true}
                        """
                ),
                "canDeserializeTrueToNonnullableField"
        );

        Assertions.assertEquals(
                new NonnullableSiNo(false),
                deser(NonnullableSiNo.class, true,
                        """
                        {"value": "NO"}
                        """
                ),
                "canDeserializeNoToNonnullableField"
        );
        Assertions.assertEquals(
                new NonnullableSiNo(false),
                deser(NonnullableSiNo.class, false,
                        """
                        {"value": false}
                        """
                ),
                "canDeserializeFalseToNonnullableField"
        );
        Assertions.assertEquals(
                new NonnullableSiNo(false),
                deser(NonnullableSiNo.class, true,
                        """
                        {"value": null}
                        """
                ),
                "can deserialize null to nonnulable field"
        );
        Assertions.assertThrows(MismatchedInputException.class, () -> {
            deser(NonnullableSiNo.class, false,
                    """
                    {"value": null}
                    """
            );
        }, "cannot deserialize null to nonnulable field");
    }

    @Test
    public void serialization() throws Exception {
        Assertions.assertEquals(
                """
                {"value":"SI"}
                """.trim(),
                ser(true, new NullableSiNo(Boolean.TRUE)),
                "can serialize Boolean.TRUE as SI"
        );
        Assertions.assertEquals(
                """
                {"value":true}
                """.trim(),
                ser(false, new NullableSiNo(Boolean.TRUE)),
                "can serialize Boolean.TRUE as true"
        );
        Assertions.assertEquals(
                """
                {"value":"NO"}
                """.trim(),
                ser(true, new NullableSiNo(Boolean.FALSE)),
                "can serialize Boolean.FALSE to NO"
        );
        Assertions.assertEquals(
                """
                {"value":false}
                """.trim(),
                ser(false, new NullableSiNo(Boolean.FALSE)),
                "can seserialize Boolean.False to false"
        );

        Assertions.assertEquals(
                """
                {"value":null}
                """.trim(),
                ser(true, new NullableSiNo(null)),
                "can serialize null"
        );
        Assertions.assertEquals(
                """
                {"value":null}
                """.trim(),
                ser(false, new NullableSiNo(null)),
                "can serialize null"
        );

        Assertions.assertEquals(
                """
                {"value":"SI"}
                """.trim(),
                ser(true, new NonnullableSiNo(true)),
                "can serialize true as SI"
        );
        Assertions.assertEquals(
                """
                {"value":true}
                """.trim(),
                ser(false, new NonnullableSiNo(true)),
                "can serialize true as true"
        );

        Assertions.assertEquals(
                """
                {"value":"NO"}
                """.trim(),
                ser(true, new NonnullableSiNo(false)),
                "can serialize false as NO"
        );
        Assertions.assertEquals(
                """
                {"value":false}
                """.trim(),
                ser(false, new NonnullableSiNo(false)),
                "can serialize false as false"
        );
    }

}
