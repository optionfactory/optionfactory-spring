package net.optionfactory.spring.data.jpa.filtering;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FilterRequestTest {

    private enum Kind {
        DOG, CAT
    }

    @Test
    public void unfilteredHasNoFilters() {
        Assertions.assertEquals(Map.of(), FilterRequest.unfiltered().filters(), "an unfiltered request requests no filter");
    }

    @Test
    public void withAddsToACopyLeavingTheOriginalUnchanged() {
        final var original = FilterRequest.unfiltered().with("a", "1");
        final var added = original.with("b", "2");
        Assertions.assertEquals(Set.of("a"), original.filters().keySet(), "the original request is not changed by with");
        Assertions.assertEquals(Set.of("a", "b"), added.filters().keySet(), "the copy carries both filters");
    }

    @Test
    public void withReplacesTheValuesOfAFilterAlreadyPresent() {
        final var replaced = FilterRequest.unfiltered().with("a", "1").with("a", "2");
        Assertions.assertArrayEquals(new String[]{"2"}, replaced.filters().get("a"), "the later values replace the earlier ones");
    }

    @Test
    public void withoutRemovesFromACopyLeavingTheOriginalUnchanged() {
        final var original = FilterRequest.unfiltered().with("a", "1").with("b", "2");
        final var removed = original.without("a");
        Assertions.assertEquals(Set.of("a", "b"), original.filters().keySet(), "the original request is not changed by without");
        Assertions.assertEquals(Set.of("b"), removed.filters().keySet(), "the copy lacks the removed filter");
    }

    @Test
    public void builderEncodesEachFilterWithItsHelper() {
        final var request = FilterRequest.builder()
                .text("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "rex"))
                .inEnum("byKind", Kind.DOG, null)
                .inList("byBreed", "beagle", "pug")
                .number("byWeight", f -> f.between(1, 5))
                .localDate("bornOn", f -> f.eq(LocalDate.of(2020, 1, 2)))
                .instant("createdAt", f -> f.gt(InstantCompare.Format.UNIX_S, Instant.ofEpochSecond(42)))
                .bool("active", f -> f.neq(true))
                .textSearch("search", "black cat")
                .build();
        Assertions.assertArrayEquals(new String[]{"CONTAINS", "IGNORE_CASE", "rex"}, request.filters().get("byName"), "text values are operator, case sensitivity and operand");
        Assertions.assertArrayEquals(new String[]{"DOG", null}, request.filters().get("byKind"), "enum constants are encoded by name, null kept");
        Assertions.assertArrayEquals(new String[]{"beagle", "pug"}, request.filters().get("byBreed"), "list values are passed as is");
        Assertions.assertArrayEquals(new String[]{"BETWEEN", "1", "5"}, request.filters().get("byWeight"), "numbers are encoded after the operator");
        Assertions.assertArrayEquals(new String[]{"EQ", "2020-01-02"}, request.filters().get("bornOn"), "dates use the default yyyy-MM-dd pattern");
        Assertions.assertArrayEquals(new String[]{"GT", "42"}, request.filters().get("createdAt"), "instants use the requested format");
        Assertions.assertArrayEquals(new String[]{"NEQ", "true"}, request.filters().get("active"), "booleans use the default truth values");
        Assertions.assertArrayEquals(new String[]{"black cat"}, request.filters().get("search"), "a text search is its query alone");
    }

    @Test
    public void builderCopiesAnotherRequestAndRemovesFilters() {
        final var base = FilterRequest.unfiltered().with("a", "1").with("b", "2");
        final var request = FilterRequest.builder()
                .with(base)
                .with("c", "3")
                .without("a")
                .build();
        Assertions.assertEquals(Set.of("b", "c"), request.filters().keySet(), "the copied filters, plus the added one, minus the removed one");
        Assertions.assertEquals(Set.of("a", "b"), base.filters().keySet(), "the copied request is not changed by the builder");
    }

    @Test
    public void builtRequestIsNotChangedByLaterBuilderCalls() {
        final var builder = FilterRequest.builder().with("a", "1");
        final var first = builder.build();
        builder.with("b", "2").without("a");
        Assertions.assertEquals(Set.of("a"), first.filters().keySet(), "a built request keeps the filters it was built with");
        Assertions.assertEquals(Set.of("b"), builder.build().filters().keySet(), "the builder keeps building after a build");
    }
}
