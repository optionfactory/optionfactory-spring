package net.optionfactory.spring.data.jpa.filtering;

import java.util.Map;

/**
 * Filter parameters indexed by filter name.
 */
public class FilterRequest {

    private static final long serialVersionUID = 1L;

    public Map<String, String[]> filters;

    public static FilterRequest of(Map<String, String[]> filters) {
        final FilterRequest filterRequest = new FilterRequest();
        filterRequest.filters = filters;
        return filterRequest;
    }

    public static FilterRequest unfiltered() {
        return of(Map.of());
    }
}
