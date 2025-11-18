package net.optionfactory.spring.marshaling.jackson.quirks;

import org.junit.Assert;
import org.junit.Test;
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
        Assert.assertEquals("canDeserializeSiToNullableField",
                new NullableSiNo(Boolean.TRUE),
                deser(NullableSiNo.class, true,
                        """
                        {"value": "SI"}
                        """
                )
        );
        Assert.assertEquals("canDeserializeBooleanToNullableField",
                new NullableSiNo(Boolean.TRUE),
                deser(NullableSiNo.class, false,
                        """
                        {"value": true}
                        """
                )
        );
        Assert.assertEquals("canDeserializeNoToNullableField",
                new NullableSiNo(Boolean.FALSE),
                deser(NullableSiNo.class, true,
                        """
                        {"value": "NO"}
                        """
                )
        );
        Assert.assertEquals("canDeserializeFalseToNullableField",
                new NullableSiNo(Boolean.FALSE),
                deser(NullableSiNo.class, false,
                        """
                        {"value": false}
                        """
                )
        );

        Assert.assertEquals("canDeserializeNullToNullableField",
                new NullableSiNo(null),
                deser(NullableSiNo.class, true,
                        """
                        {"value": null}
                        """
                )
        );
        Assert.assertEquals("canDeserializeNullToNullableField",
                new NullableSiNo(null),
                deser(NullableSiNo.class, false,
                        """
                        {"value": null}
                        """
                )
        );

        Assert.assertEquals("canDeserializeSiToNonnullableField",
                new NonnullableSiNo(true),
                deser(NonnullableSiNo.class, true,
                        """
                        {"value": "SI"}
                        """
                )
        );
        Assert.assertEquals("canDeserializeTrueToNonnullableField",
                new NonnullableSiNo(true),
                deser(NonnullableSiNo.class, false,
                        """
                        {"value": true}
                        """
                )
        );

        Assert.assertEquals("canDeserializeNoToNonnullableField",
                new NonnullableSiNo(false),
                deser(NonnullableSiNo.class, true,
                        """
                        {"value": "NO"}
                        """
                )
        );
        Assert.assertEquals("canDeserializeFalseToNonnullableField",
                new NonnullableSiNo(false),
                deser(NonnullableSiNo.class, false,
                        """
                        {"value": false}
                        """
                )
        );
        Assert.assertEquals("can deserialize null to nonnulable field",
                new NonnullableSiNo(false),
                deser(NonnullableSiNo.class, true,
                        """
                        {"value": null}
                        """
                )
        );
        Assert.assertThrows("cannot deserialize null to nonnulable field", MismatchedInputException.class, () -> {
            deser(NonnullableSiNo.class, false,
                    """
                    {"value": null}
                    """
            );
        });
    }

    @Test
    public void serialization() throws Exception {
        Assert.assertEquals("can serialize Boolean.TRUE as SI",
                """
                {"value":"SI"}
                """.trim(),
                ser(true, new NullableSiNo(Boolean.TRUE))
        );
        Assert.assertEquals("can serialize Boolean.TRUE as true",
                """
                {"value":true}
                """.trim(),
                ser(false, new NullableSiNo(Boolean.TRUE))
        );
        Assert.assertEquals("can serialize Boolean.FALSE to NO",
                """
                {"value":"NO"}
                """.trim(),
                ser(true, new NullableSiNo(Boolean.FALSE))
        );
        Assert.assertEquals("can seserialize Boolean.False to false",
                """
                {"value":false}
                """.trim(),
                ser(false, new NullableSiNo(Boolean.FALSE))
        );

        Assert.assertEquals("can serialize null",
                """
                {"value":null}
                """.trim(),
                ser(true, new NullableSiNo(null))
        );
        Assert.assertEquals("can serialize null",
                """
                {"value":null}
                """.trim(),
                ser(false, new NullableSiNo(null))
        );

        Assert.assertEquals("can serialize true as SI",
                """
                {"value":"SI"}
                """.trim(),
                ser(true, new NonnullableSiNo(true))
        );
        Assert.assertEquals("can serialize true as true",
                """
                {"value":true}
                """.trim(),
                ser(false, new NonnullableSiNo(true))
        );

        Assert.assertEquals("can serialize false as NO",
                """
                {"value":"NO"}
                """.trim(),
                ser(true, new NonnullableSiNo(false))
        );
        Assert.assertEquals("can serialize false as false",
                """
                {"value":false}
                """.trim(),
                ser(false, new NonnullableSiNo(false))
        );
    }

}
