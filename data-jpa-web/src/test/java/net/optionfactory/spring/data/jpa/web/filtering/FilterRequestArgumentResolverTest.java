package net.optionfactory.spring.data.jpa.web.filtering;

import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.json.JsonMapper;

public class FilterRequestArgumentResolverTest {

    private static FilterRequest resolve(String parameterName, String value) throws Exception {
        final var request = new MockHttpServletRequest();
        request.addParameter(parameterName, value);
        return new FilterRequestArgumentResolver(parameterName, new JsonMapper()).resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    @Test
    public void aWellFormedParameterIsResolved() throws Exception {
        final var got = resolve("filters", "{\"byName\": [\"EQ\", \"CASE_SENSITIVE\", \"rex\"]}");
        Assertions.assertArrayEquals(new String[]{"EQ", "CASE_SENSITIVE", "rex"}, got.filters().get("byName"));
    }

    @Test
    public void aParameterThatIsNotJsonIsRejectedAsThatParameter() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "{not json"));
        Assertions.assertEquals("filters", thrown.filter);
        Assertions.assertNotNull(thrown.getCause());
    }

    @Test
    public void aParameterOfTheWrongShapeIsRejectedAsThatParameter() {
        Assertions.assertEquals("filters", Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "[\"EQ\"]")).filter);
    }

    @Test
    public void aNullParameterIsRejectedAsThatParameter() {
        Assertions.assertEquals("filters", Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "null")).filter);
    }

    @Test
    public void aRejectionNamesACustomParameter() {
        Assertions.assertEquals("q", Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("q", "{not json")).filter);
    }

    @Test
    public void aFilterWithoutValuesIsRejectedAsThatFilter() {
        Assertions.assertEquals("byName", Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "{\"byName\": null}")).filter);
    }
}
