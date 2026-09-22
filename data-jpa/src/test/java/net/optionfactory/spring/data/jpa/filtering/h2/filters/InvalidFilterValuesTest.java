package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.InList;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * A malformed value is a bad request, not a bug: every conversion of client input must surface
 * as an {@link InvalidFilterRequest} rather than as whatever the underlying parser happens to
 * throw. {@code DateTimeParseException} and {@code StringIndexOutOfBoundsException} are not even
 * {@code IllegalArgumentException}s, so leaking them turns a bad request into a server error in
 * any caller mapping by exception type.
 */
@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class InvalidFilterValuesTest {

    @Entity
    @LocalDateCompare(name = "byDate", path = "date")
    @InstantCompare(name = "byInstant", path = "instant")
    @InstantCompare(name = "byEpochSeconds", path = "instant", format = InstantCompare.Format.UNIX_S)
    @InstantCompare(name = "byEpochNanos", path = "instant", format = InstantCompare.Format.UNIX_NS)
    @NumberCompare(name = "byNumber", path = "number")
    @TextCompare(name = "byText", path = "text")
    @InList(name = "inChars", path = "letter")
    @InList(name = "inNumbers", path = "number")
    public static class Root {

        @Id
        public long id;
        public LocalDate date;
        public Instant instant;
        public long number;
        public String text;
        public char letter;
    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    private RootsRepository repo;

    /**
     * Values are passed as the raw arrays a request carries: the typed {@code FilterRequest}
     * builders take a {@code LocalDate} or a {@code Number} and so cannot express a malformed
     * one, which is exactly why these values can only arrive from the wire.
     */
    private static FilterRequest request(String filter, String... values) {
        return new FilterRequest(java.util.Map.of(filter, values));
    }

    private void assertInvalidFilterRequest(String expectedFragment, FilterRequest fr) {
        final var thrown = Assertions.assertThrows(Exception.class, () -> repo.findAll(fr));
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof InvalidFilterRequest) {
                Assertions.assertTrue(t.getMessage().contains(expectedFragment), t.getMessage());
                return;
            }
        }
        Assertions.fail("expected an InvalidFilterRequest in the cause chain, got " + thrown);
    }

    @Test
    public void malformedLocalDateIsARejectedRequest() {
        assertInvalidFilterRequest("cannot parse 'not-a-date' as a local date", request("byDate", "EQ", "not-a-date"));
    }

    @Test
    public void malformedLocalDateInARangeIsARejectedRequest() {
        assertInvalidFilterRequest("cannot parse 'nope' as a local date", request("byDate", "BETWEEN", "2020-01-01", "nope"));
    }

    @Test
    public void malformedIso8601InstantIsARejectedRequest() {
        assertInvalidFilterRequest("cannot parse 'yesterday' as an instant", request("byInstant", "EQ", "yesterday"));
    }

    @Test
    public void malformedEpochSecondsIsARejectedRequest() {
        assertInvalidFilterRequest("cannot parse 'soon' as an instant", request("byEpochSeconds", "EQ", "soon"));
    }

    @Test
    public void malformedEpochNanosIsARejectedRequest() {
        assertInvalidFilterRequest("cannot parse '1.5' as an instant", request("byEpochNanos", "EQ", "1.5"));
    }

    @Test
    public void malformedNumberIsARejectedRequest() {
        assertInvalidFilterRequest("cannot convert value 'twelve'", request("byNumber", "EQ", "twelve"));
    }

    @Test
    public void malformedNumberInAListIsARejectedRequest() {
        assertInvalidFilterRequest("cannot convert value 'x'", request("inNumbers", "1", "x"));
    }

    @Test
    public void anEmptyValueForACharPropertyIsARejectedRequest() {
        assertInvalidFilterRequest("expected a single character, got ''", request("inChars", ""));
    }

    @Test
    public void aMultiCharacterValueForACharPropertyIsARejectedRequest() {
        assertInvalidFilterRequest("expected a single character, got 'ab'", request("inChars", "ab"));
    }

    @Test
    public void anUnknownCaseSensitivityIsARejectedRequest() {
        assertInvalidFilterRequest("Unknown value 'SOMETIMES' for mode", request("byText", "EQ", "SOMETIMES", "x"));
    }

    @Test
    public void anUnknownOperatorIsARejectedRequest() {
        assertInvalidFilterRequest("Unknown value 'SORTOF' for operator", request("byText", "SORTOF", "CASE_SENSITIVE", "x"));
    }
}
