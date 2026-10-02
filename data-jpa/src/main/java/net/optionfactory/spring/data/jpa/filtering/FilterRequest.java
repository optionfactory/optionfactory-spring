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
import net.optionfactory.spring.data.jpa.filtering.filters.TextSearch;

/// The filters requested by a client: for each whitelisted filter name, the values passed to that
/// filter, whose meaning (operator, case sensitivity, operands, ...) is the filter's own.
///
/// A request names filters only: the paths they read are decided where the filters are whitelisted,
/// on the entity, so a client can neither reach an unlisted property nor learn the entity layout.
/// Every requested filter is applied, combined in `AND`; requesting a name that is not whitelisted
/// on the queried entity fails the query with an
/// [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest].
///
/// ```java
/// final var request = FilterRequest.builder()
///         .text("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "rex"))
///         .inEnum("byType", PetType.DOG, PetType.CAT)
///         .localDate("bornAfter", f -> f.gt(LocalDate.of(2020, 1, 1)))
///         .build();
/// final Page<Pet> page = pets.findAll(request, PageRequest.of(0, 20));
/// ```
///
/// The value arrays are compared by identity by the record's `equals`, as arrays are.
///
/// @param filters the values of each requested filter, by filter name; a `null` map is read as no
/// filters by the repository
public record FilterRequest(Map<String, String[]> filters) {

    /// @param filter the filter name
    /// @param params the values passed to the filter
    /// @return a copy of this request with the filter added, or its values replaced; this request
    /// is left unchanged
    public FilterRequest with(String filter, String... params) {
        final var fs = new HashMap<>(filters());
        fs.put(filter, params);
        return new FilterRequest(fs);
    }

    /// @param filter the filter name
    /// @return a copy of this request without the filter; this request is left unchanged
    public FilterRequest without(String filter) {
        final var fs = new HashMap<>(filters());
        fs.remove(filter);
        return new FilterRequest(fs);
    }

    /// @return a request with no filters, matching every entity the base specification matches
    public static FilterRequest unfiltered() {
        return new FilterRequest(Map.of());
    }

    /// @return an empty builder
    public static Builder builder() {
        return new Builder();
    }

    /// Builds a [FilterRequest], using the `Filter` helpers of the built-in filter annotations to
    /// encode the values of each filter. Naming a filter again replaces its values.
    ///
    /// [#build()] hands the builder's own map to the request: a builder is meant to be discarded
    /// once built, as further changes through it would show in the built request.
    public static class Builder {

        private final Map<String, String[]> filters;

        /// Creates an empty builder.
        public Builder() {
            this.filters = new HashMap<>();
        }

        /// @param request a request whose filters are copied into this builder, replacing the
        /// values of the filters already present with the same name
        /// @return this builder
        public Builder with(FilterRequest request) {
            filters.putAll(request.filters());
            return this;
        }

        /// @param name the filter name
        /// @param values the raw values of the filter
        /// @return this builder
        public Builder with(String name, String... values) {
            filters.put(name, values);
            return this;
        }

        /// @param name the filter name to remove
        /// @return this builder
        public Builder without(String name) {
            filters.remove(name);
            return this;
        }

        /// Adds a [BooleanCompare] filter, e.g. `.bool("active", f -> f.eq(true))`.
        ///
        /// @param name the filter name
        /// @param customizer encodes the values through [BooleanCompare.Filter]
        /// @return this builder
        public Builder bool(String name, Function<BooleanCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(BooleanCompare.Filter.INSTANCE));
            return this;
        }

        /// Adds an [InEnum] filter accepting the given constants; a `null` constant matches a
        /// `NULL` column when the filter is nullable.
        ///
        /// @param name the filter name
        /// @param values the accepted constants
        /// @return this builder
        public Builder inEnum(String name, Enum<?>... values) {
            filters.put(name, InEnum.Filter.INSTANCE.in(values));
            return this;
        }

        /// @param name the filter name
        /// @param customizer encodes the values through [InEnum.Filter]
        /// @return this builder
        public Builder inEnum(String name, Function<InEnum.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(InEnum.Filter.INSTANCE));
            return this;
        }

        /// Adds an [InList] filter accepting the given values.
        ///
        /// @param name the filter name
        /// @param values the accepted values, in their string form
        /// @return this builder
        public Builder inList(String name, String... values) {
            filters.put(name, InList.Filter.INSTANCE.in(values));
            return this;
        }

        /// @param name the filter name
        /// @param customizer encodes the values through [InList.Filter]
        /// @return this builder
        public Builder inList(String name, Function<InList.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(InList.Filter.INSTANCE));
            return this;
        }

        /// Adds an [InstantCompare] filter, e.g. `.instant("createdAfter", f -> f.gt(Format.ISO_8601, since))`.
        ///
        /// @param name the filter name
        /// @param customizer encodes the values through [InstantCompare.Filter]
        /// @return this builder
        public Builder instant(String name, Function<InstantCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(InstantCompare.Filter.INSTANCE));
            return this;
        }

        /// Adds a [LocalDateCompare] filter, e.g. `.localDate("bornAfter", f -> f.gt(date))`.
        ///
        /// @param name the filter name
        /// @param customizer encodes the values through [LocalDateCompare.Filter]
        /// @return this builder
        public Builder localDate(String name, Function<LocalDateCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(LocalDateCompare.Filter.INSTANCE));
            return this;
        }

        /// Adds a [NumberCompare] filter, e.g. `.number("byWeight", f -> f.between(1, 5))`.
        ///
        /// @param name the filter name
        /// @param customizer encodes the values through [NumberCompare.Filter]
        /// @return this builder
        public Builder number(String name, Function<NumberCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(NumberCompare.Filter.INSTANCE));
            return this;
        }

        /// Adds a [TextCompare] filter, e.g. `.text("byName", f -> f.startsWith(CaseSensitivity.IGNORE_CASE, "re"))`.
        ///
        /// @param name the filter name
        /// @param customizer encodes the values through [TextCompare.Filter]
        /// @return this builder
        public Builder text(String name, Function<TextCompare.Filter, String[]> customizer) {
            filters.put(name, customizer.apply(TextCompare.Filter.INSTANCE));
            return this;
        }

        /// Adds a [TextSearch] filter.
        ///
        /// @param name the filter name
        /// @param query the client's search text, interpreted according to the filter's
        /// [TextSearch#syntax()]
        /// @return this builder
        public Builder textSearch(String name, String query) {
            filters.put(name, TextSearch.Filter.INSTANCE.of(query));
            return this;
        }

        /// @return a request holding a copy of the filters added so far, left unchanged by later calls
        /// to this builder
        public FilterRequest build() {
            return new FilterRequest(new HashMap<>(filters));
        }
    }
}
