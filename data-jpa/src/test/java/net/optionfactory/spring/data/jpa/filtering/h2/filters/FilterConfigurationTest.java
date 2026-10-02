package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.metamodel.EntityType;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/// Whitelistings that can never work are rejected when the filter is created, i.e. when the
/// repository is built, instead of failing every request or with an unrelated exception.
///
/// The entity has no repository: its filters are created explicitly, so the context still starts.
@SpringJUnitConfig(HibernateOnH2TestConfig.class)
public class FilterConfigurationTest {

    @Entity
    @NumberCompare(name = "byLetter", path = "letter")
    @NumberCompare(name = "numberWithoutOperators", path = "weight", operators = {})
    @TextCompare(name = "textWithoutOperators", path = "name", operators = {})
    @TextCompare(name = "textWithoutCaseSensitivity", path = "name", caseSensitivity = {})
    @BooleanCompare(name = "booleanWithoutOperators", path = "flag", operators = {})
    @LocalDateCompare(name = "localDateWithoutOperators", path = "birthday", operators = {})
    @InstantCompare(name = "instantWithoutOperators", path = "seenAt", operators = {})
    public static class Misconfigured {

        @Id
        public long id;
        public char letter;
        public Integer weight;
        public String name;
        public Boolean flag;
        public LocalDate birthday;
        public Instant seenAt;
    }

    @Inject
    private EntityManagerFactory emf;

    @Test
    public void numberCompareOnACharPropertyIsRejected() {
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, () -> new NumberCompare.NumberCompareFilter(annotation(NumberCompare.class, "byLetter"), entity()), "a char property is not numeric, and is rejected when the filter is created");
        Assertions.assertTrue(thrown.getMessage().contains("letter"), "the rejection names the property: " + thrown.getMessage());
    }

    @Test
    public void numberCompareWithoutOperatorsIsRejected() {
        assertRejectedForNoOperators(() -> new NumberCompare.NumberCompareFilter(annotation(NumberCompare.class, "numberWithoutOperators"), entity()), "operators");
    }

    @Test
    public void textCompareWithoutOperatorsIsRejected() {
        assertRejectedForNoOperators(() -> new TextCompare.TextCompareFilter(annotation(TextCompare.class, "textWithoutOperators"), entity()), "operators");
    }

    @Test
    public void textCompareWithoutCaseSensitivityIsRejected() {
        assertRejectedForNoOperators(() -> new TextCompare.TextCompareFilter(annotation(TextCompare.class, "textWithoutCaseSensitivity"), entity()), "caseSensitivity");
    }

    @Test
    public void booleanCompareWithoutOperatorsIsRejected() {
        assertRejectedForNoOperators(() -> new BooleanCompare.BooleanCompareFilter(annotation(BooleanCompare.class, "booleanWithoutOperators"), entity()), "operators");
    }

    @Test
    public void localDateCompareWithoutOperatorsIsRejected() {
        assertRejectedForNoOperators(() -> new LocalDateCompare.LocalDateCompareFilter(annotation(LocalDateCompare.class, "localDateWithoutOperators"), entity()), "operators");
    }

    @Test
    public void instantCompareWithoutOperatorsIsRejected() {
        assertRejectedForNoOperators(() -> new InstantCompare.InstantCompareFilter(annotation(InstantCompare.class, "instantWithoutOperators"), entity()), "operators");
    }

    private static void assertRejectedForNoOperators(Runnable creation, String attribute) {
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, creation::run, "an empty " + attribute + " whitelists nothing, and is rejected when the filter is created");
        Assertions.assertTrue(thrown.getMessage().contains(attribute + " must not be empty"), "the rejection names the empty attribute: " + thrown.getMessage());
    }

    private EntityType<Misconfigured> entity() {
        return emf.getMetamodel().entity(Misconfigured.class);
    }

    private static <A extends Annotation> A annotation(Class<A> type, String name) {
        return Stream.of(Misconfigured.class.getAnnotationsByType(type))
                .filter(a -> name.equals(nameOf(a)))
                .findFirst()
                .orElseThrow();
    }

    private static String nameOf(Annotation annotation) {
        try {
            return (String) annotation.annotationType().getMethod("name").invoke(annotation);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
