package net.optionfactory.spring.data.jpa.web.filtering;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class FilterRequestArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String DEFAULT_PARAMETER_NAME = "filters";

    private static final TypeReference<HashMap<String, String[]>> PARAMETERS_TYPE = new TypeReference<HashMap<String, String[]>>() {

    };
    private final String parameterName;
    private final ObjectMapper mapper;

    public FilterRequestArgumentResolver(String parameterName, ObjectMapper mapper) {
        this.parameterName = parameterName;
        this.mapper = mapper;
    }

    public FilterRequestArgumentResolver(ObjectMapper mapper) {
        this(DEFAULT_PARAMETER_NAME, mapper);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return FilterRequest.class.equals(parameter.getParameterType());
    }

    @Override
    public FilterRequest resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        final String[] values = webRequest.getParameterValues(parameterName);
        if (values == null || values.length == 0) {
            return FilterRequest.unfiltered();
        }
        final var parameters = Stream.of(values)
                .filter(value -> !value.isBlank())
                .flatMap(value -> {
                    try {
                        return mapper.readValue(value, PARAMETERS_TYPE).entrySet().stream();
                    } catch (JsonProcessingException exception) {
                        throw new IllegalArgumentException(exception);
                    }
                })
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> b, HashMap::new));
        return new FilterRequest(parameters);
    }

}
