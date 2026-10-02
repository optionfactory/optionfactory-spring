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

/// Resolves a [FilterRequest] controller argument from a request parameter carrying a json
/// object that maps each filter name to its array of values.
///
/// ```java
/// @Override
/// public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
///     resolvers.add(new FilterRequestArgumentResolver(jsonMapper));
/// }
///
/// @GetMapping("/pets")
/// public Page<Pet> search(FilterRequest filters, Pageable pageable) {
///     return pets.findAll(filters, pageable);
/// }
/// ```
///
/// A request such as `GET /pets?filters={"byName":["CONTAINS","IGNORE_CASE","rex"]}` then
/// resolves to a request for the `byName` filter with those three values. A missing parameter
/// resolves to [FilterRequest#unfiltered()]. The parameter may be repeated: the objects are merged,
/// a filter named again by a later one replacing the values of the earlier, and blank occurrences
/// are skipped. `null` elements inside a values array are kept, as several filters give `null` a
/// meaning of its own.
///
/// The resolver only checks the shape of the request, and bounds its size so that a client cannot
/// make the application build arbitrarily large queries; whether the filters exist and accept the
/// values is decided later, by the repository. Every rejection is an [InvalidFilterRequest]: its
/// `filter` names the offending filter, or the parameter itself when the parameter is not a json
/// object of arrays.
public class FilterRequestArgumentResolver implements HandlerMethodArgumentResolver {

    /// The request parameter read when none is configured.
    public static final String DEFAULT_PARAMETER_NAME = "filters";
    /// The maximum number of distinct filters accepted when no limit is configured.
    public static final int DEFAULT_MAX_FILTERS = 64;
    /// The maximum number of values accepted for one filter when no limit is configured.
    public static final int DEFAULT_MAX_VALUES_PER_FILTER = 1024;

    private static final TypeReference<HashMap<String, String[]>> PARAMETERS_TYPE = new TypeReference<HashMap<String, String[]>>() {

    };
    private final String parameterName;
    private final JsonMapper mapper;
    private final int maxFilters;
    private final int maxValuesPerFilter;

    /// Reads the given parameter, with the default limits.
    ///
    /// @param parameterName the request parameter carrying the filters
    /// @param mapper the mapper parsing the parameter value
    public FilterRequestArgumentResolver(String parameterName, JsonMapper mapper) {
        this(parameterName, mapper, DEFAULT_MAX_FILTERS, DEFAULT_MAX_VALUES_PER_FILTER);
    }

    /// Reads the [#DEFAULT_PARAMETER_NAME] parameter, with the default limits.
    ///
    /// @param mapper the mapper parsing the parameter value
    public FilterRequestArgumentResolver(JsonMapper mapper) {
        this(DEFAULT_PARAMETER_NAME, mapper);
    }

    /// @param parameterName the request parameter carrying the filters
    /// @param mapper the mapper parsing the parameter value
    /// @param maxFilters the maximum number of distinct filter names in a request, counted across
    /// every occurrence of the parameter
    /// @param maxValuesPerFilter the maximum length of the values array of a single filter
    /// @throws IllegalArgumentException when either limit is not positive
    public FilterRequestArgumentResolver(String parameterName, JsonMapper mapper, int maxFilters, int maxValuesPerFilter) {
        if (maxFilters < 1 || maxValuesPerFilter < 1) {
            throw new IllegalArgumentException("maxFilters and maxValuesPerFilter must be positive");
        }
        this.parameterName = parameterName;
        this.mapper = mapper;
        this.maxFilters = maxFilters;
        this.maxValuesPerFilter = maxValuesPerFilter;
    }

    /// @param parameter the controller method parameter
    /// @return whether the parameter is declared exactly as a [FilterRequest]
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return FilterRequest.class.equals(parameter.getParameterType());
    }

    /// @return the filters carried by the request, or [FilterRequest#unfiltered()] when the
    /// parameter is absent
    /// @throws InvalidFilterRequest when an occurrence of the parameter is not a json object, or is
    /// `null`, naming the parameter; when a filter maps to `null` instead of an array, has more
    /// than the allowed values, or exceeds the allowed number of filters, naming that filter
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
