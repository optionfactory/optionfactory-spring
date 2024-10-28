package net.optionfactory.spring.data.jpa.filtering;

import java.util.HashMap;
import java.util.Map;

/**
 * Filter parameters indexed by filter name.
 */
public record FilterRequest(Map<String, String[]> filters) {

    public FilterRequest with(String filter, String... params) {
        final var fs = new HashMap<>(filters());
        fs.put(filter, params);
        return FilterRequest.of(fs);
    }

    public FilterRequest without(String filter) {
        final var fs = new HashMap<>(filters());
        fs.remove(filter);
        return FilterRequest.of(fs);
    }

    public static FilterRequest unfiltered() {
        return of(Map.of());
    }

    public static FilterRequest of(Map<String, String[]> filters) {
        return new FilterRequest(filters);
    }

    public static FilterRequest of(String filter, String... params) {
        return new FilterRequest(Map.of(filter, params));
    }
}
