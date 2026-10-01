package net.optionfactory.spring.data.jpa.web.filtering;

import java.util.HashMap;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class FilterRequestArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String DEFAULT_PARAMETER_NAME = "filters";
    public static final int DEFAULT_MAX_FILTERS = 64;
    public static final int DEFAULT_MAX_VALUES_PER_FILTER = 1024;

    private static final TypeReference<HashMap<String, String[]>> PARAMETERS_TYPE = new TypeReference<HashMap<String, String[]>>() {

    };
    private final String parameterName;
    private final JsonMapper mapper;
    private final int maxFilters;
    private final int maxValuesPerFilter;

    public FilterRequestArgumentResolver(String parameterName, JsonMapper mapper) {
        this(parameterName, mapper, DEFAULT_MAX_FILTERS, DEFAULT_MAX_VALUES_PER_FILTER);
    }

    public FilterRequestArgumentResolver(JsonMapper mapper) {
        this(DEFAULT_PARAMETER_NAME, mapper);
    }

    public FilterRequestArgumentResolver(String parameterName, JsonMapper mapper, int maxFilters, int maxValuesPerFilter) {
        if (maxFilters < 1 || maxValuesPerFilter < 1) {
            throw new IllegalArgumentException("maxFilters and maxValuesPerFilter must be positive");
        }
        this.parameterName = parameterName;
        this.mapper = mapper;
        this.maxFilters = maxFilters;
        this.maxValuesPerFilter = maxValuesPerFilter;
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
        final var parameters = new HashMap<String, String[]>();
        for (final String value : values) {
            if (value.isBlank()) {
                continue;
            }
            final HashMap<String, String[]> parsed;
            try {
                parsed = mapper.readValue(value, PARAMETERS_TYPE);
            } catch (JacksonException ex) {
                throw unreadable(ex);
            }
            if (parsed == null) {
                throw unreadable(null);
            }
            for (final var filter : parsed.entrySet()) {
                if (filter.getValue() == null) {
                    throw new InvalidFilterRequest(filter.getKey(), null, "expected an array of values");
                }
                if (filter.getValue().length > maxValuesPerFilter) {
                    throw new InvalidFilterRequest(filter.getKey(), null, String.format("too many values: %d, maximum is %d", filter.getValue().length, maxValuesPerFilter));
                }
                parameters.put(filter.getKey(), filter.getValue());
                if (parameters.size() > maxFilters) {
                    throw new InvalidFilterRequest(filter.getKey(), null, String.format("too many filters: %d, maximum is %d", parameters.size(), maxFilters));
                }
            }
        }
        return new FilterRequest(parameters);
    }

    private InvalidFilterRequest unreadable(@Nullable JacksonException cause) {
        final var rejection = new InvalidFilterRequest(parameterName, null, "expected a json object mapping filter names to arrays of values");
        if (cause != null) {
            rejection.initCause(cause);
        }
        return rejection;
    }

}
