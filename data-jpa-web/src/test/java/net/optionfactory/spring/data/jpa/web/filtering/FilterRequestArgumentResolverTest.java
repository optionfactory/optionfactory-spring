package net.optionfactory.spring.data.jpa.web.filtering;

import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
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
        Assertions.assertArrayEquals(new String[]{"EQ", "CASE_SENSITIVE", "rex"}, got.filters().get("byName"), "the filter values are resolved as sent, in order");
    }

    @Test
    public void aParameterThatIsNotJsonIsRejectedAsThatParameter() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "{not json"), "a parameter that is not json is rejected");
        Assertions.assertEquals("filters", thrown.filter, "an unparseable parameter is rejected naming the parameter");
        Assertions.assertNotNull(thrown.getCause(), "the json parsing failure is kept as the cause");
    }

    @Test
    public void aParameterOfTheWrongShapeIsRejectedAsThatParameter() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "[\"EQ\"]"), "a json array is not a filter request");
        Assertions.assertEquals("filters", thrown.filter, "a parameter of the wrong shape is rejected naming the parameter");
    }

    @Test
    public void aNullParameterIsRejectedAsThatParameter() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "null"), "a json null is not a filter request");
        Assertions.assertEquals("filters", thrown.filter, "a null parameter is rejected naming the parameter");
    }

    @Test
    public void aRejectionNamesACustomParameter() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("q", "{not json"), "an unparseable custom parameter is rejected");
        Assertions.assertEquals("q", thrown.filter, "the rejection names the configured parameter, not the default one");
    }

    @Test
    public void aFilterWithoutValuesIsRejectedAsThatFilter() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "{\"byName\": null}"), "a filter mapped to null instead of an array is rejected");
        Assertions.assertEquals("byName", thrown.filter, "the rejection names the filter without values");
    }

    private static FilterRequest resolve(String parameterName, String value, int maxFilters, int maxValuesPerFilter) throws Exception {
        final var request = new MockHttpServletRequest();
        request.addParameter(parameterName, value);
        return new FilterRequestArgumentResolver(parameterName, new JsonMapper(), maxFilters, maxValuesPerFilter).resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    @Test
    public void aFilterWithTooManyValuesIsRejectedAsThatFilter() {
        final var values = new StringBuilder("{\"byName\": [");
        for (int i = 0; i < 3; i++) {
            values.append(i == 0 ? "\"v" : ", \"v").append(i).append("\"");
        }
        values.append("]}");
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", values.toString(), 64, 2), "more values than allowed are rejected");
        Assertions.assertEquals("byName", thrown.filter, "the rejection names the filter with too many values");
        Assertions.assertEquals("too many values: 3, maximum is 2", thrown.reason, "the reason states the count and the limit");
    }

    @Test
    public void tooManyDistinctFiltersAreRejected() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolve("filters", "{\"a\": [\"v\"], \"b\": [\"v\"], \"c\": [\"v\"]}", 2, 1024), "more filters than allowed are rejected");
        Assertions.assertEquals("too many filters: 3, maximum is 2", thrown.reason, "the reason states the count and the limit");
    }

    @Test
    public void repeatedFilterNamesCountOnce() throws Exception {
        final var got = resolve("filters", "{\"byName\": [\"v\"]}", 1, 1024);
        Assertions.assertArrayEquals(new String[]{"v"}, got.filters().get("byName"), "a single filter fits a limit of one");
    }

    private static FilterRequest resolveAll(int maxFilters, String... values) throws Exception {
        final var request = new MockHttpServletRequest();
        for (final String value : values) {
            request.addParameter("filters", value);
        }
        return new FilterRequestArgumentResolver("filters", new JsonMapper(), maxFilters, 1024).resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    @Test
    public void aMissingParameterResolvesToAnUnfilteredRequest() throws Exception {
        final var got = new FilterRequestArgumentResolver(new JsonMapper()).resolveArgument(null, null, new ServletWebRequest(new MockHttpServletRequest()), null);
        Assertions.assertEquals(Map.of(), got.filters(), "no parameter means no filters");
    }

    @Test
    public void theDefaultParameterIsNamedFilters() throws Exception {
        final var request = new MockHttpServletRequest();
        request.addParameter("filters", "{\"byName\": [\"v\"]}");
        final var got = new FilterRequestArgumentResolver(new JsonMapper()).resolveArgument(null, null, new ServletWebRequest(request), null);
        Assertions.assertEquals(Set.of("byName"), got.filters().keySet(), "the default resolver reads the filters parameter");
    }

    @Test
    public void repeatedParametersAreMergedWithLaterValuesWinning() throws Exception {
        final var got = resolveAll(64, "{\"a\": [\"first\"], \"b\": [\"v\"]}", "{\"a\": [\"second\"]}");
        Assertions.assertEquals(Set.of("a", "b"), got.filters().keySet(), "filters of every occurrence are merged");
        Assertions.assertArrayEquals(new String[]{"second"}, got.filters().get("a"), "a filter repeated by a later occurrence takes its values");
    }

    @Test
    public void aFilterRepeatedAcrossParametersCountsOnceTowardsTheLimit() throws Exception {
        final var got = resolveAll(1, "{\"a\": [\"first\"]}", "{\"a\": [\"second\"]}");
        Assertions.assertArrayEquals(new String[]{"second"}, got.filters().get("a"), "the same filter named twice is one filter");
    }

    @Test
    public void distinctFiltersAreCountedAcrossParameters() {
        final var thrown = Assertions.assertThrows(InvalidFilterRequest.class, () -> resolveAll(1, "{\"a\": [\"v\"]}", "{\"b\": [\"v\"]}"), "the limit applies to the merged request");
        Assertions.assertEquals("b", thrown.filter, "the rejection names the filter exceeding the limit");
    }

    @Test
    public void blankParametersAreSkipped() throws Exception {
        final var got = resolveAll(64, "  ", "{\"a\": [\"v\"]}");
        Assertions.assertEquals(Set.of("a"), got.filters().keySet(), "a blank occurrence contributes nothing and is not rejected");
    }

    @Test
    public void nullValuesInsideTheArrayAreKept() throws Exception {
        final var got = resolve("filters", "{\"byType\": [\"DOG\", null]}");
        Assertions.assertArrayEquals(new String[]{"DOG", null}, got.filters().get("byType"), "null elements reach the filter, which may give them a meaning");
    }

    @Test
    public void nonPositiveLimitsAreRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new FilterRequestArgumentResolver("filters", new JsonMapper(), 0, 1), "maxFilters must be positive");
        Assertions.assertThrows(IllegalArgumentException.class, () -> new FilterRequestArgumentResolver("filters", new JsonMapper(), 1, 0), "maxValuesPerFilter must be positive");
    }

    public static class Handlers {

        public void handle(FilterRequest filters, Object other) {
        }
    }

    @Test
    public void onlyFilterRequestParametersAreSupported() throws Exception {
        final var resolver = new FilterRequestArgumentResolver(new JsonMapper());
        final var method = Handlers.class.getMethod("handle", FilterRequest.class, Object.class);
        Assertions.assertTrue(resolver.supportsParameter(new MethodParameter(method, 0)), "a FilterRequest parameter is supported");
        Assertions.assertFalse(resolver.supportsParameter(new MethodParameter(method, 1)), "a parameter of another type is not supported");
    }
}
