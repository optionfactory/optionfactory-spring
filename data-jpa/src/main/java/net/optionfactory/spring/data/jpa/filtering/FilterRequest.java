package net.optionfactory.spring.data.jpa.filtering;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.InList;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

/**
 * Filter parameters indexed by filter name.
 */
public record FilterRequest(Map<String, String[]> filters) {

    public FilterRequest with(String filter, String... params) {
        final var fs = new HashMap<>(filters());
        fs.put(filter, params);
        return new FilterRequest(fs);
    }

    public FilterRequest without(String filter) {
        final var fs = new HashMap<>(filters());
        fs.remove(filter);
        return new FilterRequest(fs);
    }

    public static FilterRequest unfiltered() {
        return new FilterRequest(Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<String, String[]> filters;

        public Builder() {
            this.filters = new HashMap<>();
        }

        public Builder with(FilterRequest request) {
            filters.putAll(request.filters());
            return this;
        }

        public Builder with(String name, String... values) {
            filters.put(name, values);
            return this;
        }

        public Builder without(String name) {
            filters.remove(name);
            return this;
        }

        public Builder bool(String name, Function<BooleanCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(BooleanCompare.Filter.INSTANCE));
            return this;
        }
        
        public Builder inEnum(String name, Enum<?>... values) {
            filters.put(name, InEnum.Filter.INSTANCE.in(values));
            return this;
        }

        public Builder inEnum(String name, Function<InEnum.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(InEnum.Filter.INSTANCE));
            return this;
        }

        public Builder inList(String name, String... values) {
            filters.put(name, InList.Filter.INSTANCE.in(values));
            return this;
        }

        public Builder inList(String name, Function<InList.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(InList.Filter.INSTANCE));
            return this;
        }

        public Builder instant(String name, Function<InstantCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(InstantCompare.Filter.INSTANCE));
            return this;
        }

        public Builder localDate(String name, Function<LocalDateCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(LocalDateCompare.Filter.INSTANCE));
            return this;
        }

        public Builder number(String name, Function<NumberCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(NumberCompare.Filter.INSTANCE));
            return this;
        }

        public Builder text(String name, Function<TextCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(TextCompare.Filter.INSTANCE));
            return this;
        }

        public FilterRequest build() {
            return new FilterRequest(filters);
        }
    }
}
