package net.optionfactory.spring.upstream.values;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.service.invoker.HttpRequestValues;

public class UpstreamAnnotatedPathVariableTransformerTest {

    private static UpstreamAnnotatedPathVariableTransformer transformer() {
        final var transformer = new UpstreamAnnotatedPathVariableTransformer();
        transformer.preprocess(AnnotatedValuesClient.class, AnnotatedValues.EXPRESSIONS, AnnotatedValues.ENDPOINTS);
        return transformer;
    }

    private static HttpRequestValues values() {
        return HttpRequestValues.builder()
                .setHttpMethod(HttpMethod.GET)
                .setUriTemplate("/users/{id}/{other}")
                .setUriVariable("id", "from-parameter")
                .setUriVariable("other", "kept")
                .build();
    }

    @Test
    public void theLastAnnotationForAVariableWins() {
        final var got = transformer().transform(AnnotatedValues.invocation("annotated", "alice"), values());
        Assertions.assertEquals(Map.of("id", "alice-last", "other", "kept"), got.getUriVariables(), "annotations must overwrite the parameter value, the last one winning, and keep the other variables");
        Assertions.assertEquals("/users/{id}/{other}", got.getUriTemplate(), "the uri template must be kept");
    }

    @Test
    public void endpointsWithoutAnnotationsKeepTheirValues() {
        final var values = values();
        Assertions.assertSame(values, transformer().transform(AnnotatedValues.invocation("bare", "alice"), values), "an endpoint without annotations must get the very same values back");
    }

    @Test
    public void theApiVersionAndTheBodyValueTypeAreKept() {
        final var builder = HttpRequestValues.builder()
                .setHttpMethod(HttpMethod.POST)
                .setUriTemplate("/users/{id}")
                .setApiVersion("1.2");
        final var bodyType = new ParameterizedTypeReference<List<String>>() {
        };
        builder.setBodyValue(List.of("a"), bodyType);
        final var got = transformer().transform(AnnotatedValues.invocation("annotated", "alice"), builder.build());
        Assertions.assertEquals("1.2", got.getApiVersion(), "the api version must be kept");
        Assertions.assertEquals(bodyType, got.getBodyValueType(), "the body value type must be kept");
        Assertions.assertEquals(List.of("a"), got.getBodyValue(), "the body value must be kept");
    }
}
