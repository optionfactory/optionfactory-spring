package net.optionfactory.spring.marshaling.jackson.quirks;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.adapters.AnnotatedDeserializerModifier;
import net.optionfactory.spring.marshaling.jackson.quirks.adapters.AnnotatedSerializerModifier;
import net.optionfactory.spring.marshaling.jackson.quirks.bool.BooleanQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.text.RenameQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.text.ScreamQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.text.TrimQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.time.LocalDateAsIsoInstantQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.time.LocalDateTimeAsIsoInstantQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.time.TemporalFormatQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.time.TimestampQuirkHandler;
import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleModule;

/// Annotations adapting the json representation of single properties to the quirks of an external
/// api (`"SI"`/`"NO"` booleans, epoch timestamps, dates sent as instants, odd property names, ...),
/// without resorting to custom types or to mapper-wide settings that would affect every other
/// payload.
///
/// The annotations are inert by themselves: they take effect on the mapper the module built here
/// is registered on, and leave every other mapper untouched.
///
/// ```java
/// final var mapper = JsonMapper.builder()
///         .addModule(Quirks.defaults().build())
///         .build();
///
/// public record Payment(
///         @Quirks.Bool boolean settled,
///         @Quirks.Timestamp(millis = false) Instant at,
///         @Quirks.Rename("Amount") long amount) {
/// }
/// ```
///
/// An annotation is found on the property's field, accessor or creator parameter (a record
/// component works). Each one is handled by a [QuirkHandler], invoked once per annotated property
/// when jackson builds the serializer or deserializer of the bean; a misplaced annotation, such as
/// [Bool] on a `String`, throws an `IllegalStateException` at that point, which is the first
/// (de)serialization of the type or a `writerFor`/`readerFor` of it.
public interface Quirks {

    /// Represents a `boolean` or `Boolean` property as two strings, `"SI"` and `"NO"` by default.
    ///
    /// When deserializing, the text of any scalar token is compared with [#t()] and [#f()],
    /// case-sensitively: anything else, a json `true` included under the defaults, fails with a
    /// `MismatchedInputException`; tokens such as `1` or `true` match when they are the configured
    /// strings. A json `null` deserializes to `null` for a `Boolean`, while for a `boolean` a `null`,
    /// or a missing creator property such as a record component, fails with a
    /// `MismatchedInputException`. A `null` `Boolean` serializes as `null`.
    ///
    /// Throws an `IllegalStateException` on a property of any other type.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Bool {

        /// @return the string representing `true`
        String t() default "SI";

        /// @return the string representing `false`
        String f() default "NO";
    }

    /// Represents a [java.time.LocalDate] property as the ISO instant of the start of that day in a
    /// time zone, e.g. `2024-01-02` as `"2024-01-01T23:00:00Z"` in `Europe/Rome`, or
    /// `"2024-01-02T00:00:00Z"` in the default `UTC`.
    ///
    /// Serialization moves the date by [#ldoffset()] [#ldunit()], takes the start of that day in
    /// the zone, moves the resulting instant by [#ioffset()] [#iunit()] and writes it as
    /// `Instant.toString()` does; deserialization reverses the steps, dropping the time of day the
    /// instant has in the zone. The offsets express conventions such as the last second of the day:
    ///
    /// ```java
    /// @Quirks.LocalDateAsIsoInstant(ldoffset = 1, ioffset = -1, iunit = ChronoUnit.SECONDS)
    /// LocalDate until;
    /// ```
    ///
    /// writes `2024-01-02` as `"2024-01-02T23:59:59Z"`, and reads it back as `2024-01-02`.
    ///
    /// Deserialization accepts a string holding an ISO instant (with `Z` or a numeric offset), and
    /// `null`; any other token, a blank or an unparseable string fail with a
    /// `MismatchedInputException`. Throws an `IllegalStateException` on a property of any other
    /// type, and a `DateTimeException` on an unknown zone, when the (de)serializer is built. An
    /// [#iunit()] longer than `DAYS`, or an [#ldunit()] shorter than `DAYS`, fail every value.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalDateAsIsoInstant {

        /// @return the id of the zone whose start of day is the instant, as `ZoneId.of`
        String value() default "UTC";

        /// @return the amount of [#iunit()] added to the instant when serializing, and subtracted
        /// when deserializing
        int ioffset() default 0;

        /// @return the unit of [#ioffset()], one an `Instant` supports (up to `DAYS`)
        ChronoUnit iunit() default ChronoUnit.HOURS;

        /// @return the amount of [#ldunit()] added to the date when serializing, and subtracted
        /// when deserializing
        int ldoffset() default 0;

        /// @return the unit of [#ldoffset()], one a `LocalDate` supports (`DAYS` and longer)
        ChronoUnit ldunit() default ChronoUnit.DAYS;
    }

    /// Represents a [java.time.LocalDateTime] property as the ISO instant it identifies in a time
    /// zone, e.g. `2024-01-02T10:00:00` as `"2024-01-02T09:00:00Z"` in `Europe/Rome`, or
    /// `"2024-01-02T10:00:00Z"` in the default `UTC`.
    ///
    /// Serialization moves the date-time by [#ldoffset()] [#ldunit()], places it in the zone (a
    /// local time falling in a daylight saving gap is shifted forward, as
    /// `LocalDateTime.atZone` does), moves the resulting instant by [#ioffset()] [#iunit()] and
    /// writes it as `Instant.toString()` does; deserialization reverses the steps.
    ///
    /// Deserialization accepts a string holding an ISO instant (with `Z` or a numeric offset), and
    /// `null`; any other token, a blank or an unparseable string fail with a
    /// `MismatchedInputException`. Throws an `IllegalStateException` on a property of any other
    /// type, and a `DateTimeException` on an unknown zone, when the (de)serializer is built. An
    /// [#iunit()] longer than `DAYS` fails every value.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalDateTimeAsIsoInstant {

        /// @return the id of the zone the local date-time is placed in, as `ZoneId.of`
        String value() default "UTC";

        /// @return the amount of [#iunit()] added to the instant when serializing, and subtracted
        /// when deserializing
        int ioffset() default 0;

        /// @return the unit of [#ioffset()], one an `Instant` supports (up to `DAYS`)
        ChronoUnit iunit() default ChronoUnit.HOURS;

        /// @return the amount of [#ldunit()] added to the date-time when serializing, and
        /// subtracted when deserializing
        int ldoffset() default 0;

        /// @return the unit of [#ldoffset()]
        ChronoUnit ldunit() default ChronoUnit.DAYS;
    }

    /// Represents a `java.time` property as a string in a custom
    /// [java.time.format.DateTimeFormatter] pattern, e.g. `"dd/MM/yyyy"`.
    ///
    /// The formatter uses the JVM default locale, which matters for the pattern letters producing
    /// text, such as `MMM` or `EEE`, and has no zone, so an `Instant` can be deserialized, from a
    /// pattern carrying an offset, but not serialized.
    ///
    /// Deserialization supports `LocalDate`, `LocalDateTime`, `LocalTime`, `Instant`,
    /// `OffsetDateTime`, `OffsetTime`, `ZonedDateTime`, `Year`, `YearMonth`, `MonthDay` and
    /// `ZoneOffset`, and throws an `IllegalStateException` on any other type when the
    /// deserializer is built; it accepts a string and `null`, and fails any other token, a blank
    /// or an unparseable string with a `MismatchedInputException`. Serialization does not check the
    /// type: a value that is not a `TemporalAccessor`, or lacks a field of the pattern, fails when
    /// written. An invalid pattern fails every (de)serialization of the type with an
    /// `InvalidDefinitionException`.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TemporalFormat {

        /// @return the pattern, as `DateTimeFormatter.ofPattern`
        String value();
    }

    /// Represents a [java.time.Instant] property as a number of milliseconds, or of seconds, since
    /// the epoch.
    ///
    /// Deserialization accepts an integer, a string holding an integer (surrounding whitespace
    /// allowed) and `null`; a decimal number, any other token, a blank or a non-numeric string fail
    /// with a `MismatchedInputException`. Serialization drops the precision the unit cannot carry
    /// (`1.999` seconds are written as `1`).
    ///
    /// The type is not checked: the annotation belongs on `Instant` properties only, a property of
    /// another type is written without a value, producing invalid json, and cannot be read.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Timestamp {

        /// @return true for milliseconds since the epoch, false for seconds
        boolean millis() default true;
    }

    /// Gives a property a fixed json name, both ways, e.g. a name that is not a valid java
    /// identifier, without a mapper-wide naming strategy.
    ///
    /// Applied after [Scream] by [Quirks#defaults()], so it wins when both are present. For a
    /// property deserialized through a field or setter (not a creator parameter or record
    /// component) the original name keeps being accepted as well.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Rename {

        /// @return the json name of the property
        String value();
    }

    /// Names a camelCase property in SCREAMING_SNAKE_CASE, both ways, e.g. `assignedValue` as
    /// `ASSIGNED_VALUE`.
    ///
    /// Every uppercase letter but the first character starts a new word, so acronyms are split
    /// letter by letter (`userID` is `USER_I_D`), while digits do not start one.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Scream {

    }

    /// Strips leading and trailing whitespace (as `String.trim`) from a `String` property, both
    /// ways; a blank string becomes empty, and `null` stays `null`.
    ///
    /// Deserialization accepts strings and `null` only: a number or a boolean, which jackson would
    /// otherwise coerce to a string, fail with a `MismatchedInputException`. Throws an
    /// `IllegalStateException` on a property of any other type.
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Trim {
    }

    /// A builder with the handlers of every annotation in [Quirks], in the order [Bool],
    /// [LocalDateAsIsoInstant], [LocalDateTimeAsIsoInstant], [TemporalFormat], [Timestamp],
    /// [Scream], [Rename], [Trim].
    ///
    /// @return a new builder, to which further handlers can be added
    public static Builder defaults() {
        return new Builder()
                .add(new BooleanQuirkHandler())
                .add(new LocalDateAsIsoInstantQuirkHandler())
                .add(new LocalDateTimeAsIsoInstantQuirkHandler())
                .add(new TemporalFormatQuirkHandler())
                .add(new TimestampQuirkHandler())
                .add(new ScreamQuirkHandler())
                .add(new RenameQuirkHandler())
                .add(new TrimQuirkHandler());
    }

    /// A builder without handlers, to activate a subset of the annotations or custom
    /// [QuirkHandler]s only; the annotations without a handler are ignored.
    ///
    /// @return a new empty builder
    public static Builder empty() {
        return new Builder();
    }

    /// Collects the [QuirkHandler]s and builds the jackson module applying them.
    ///
    /// Not thread-safe; meant to be configured once, at mapper construction.
    public static class Builder {

        private final List<QuirkHandler<?>> handlers = new ArrayList<>();

        /// Adds a handler. Handlers are applied to each property in the order they are added, each
        /// one receiving the property as left by the previous ones.
        ///
        /// @param q the handler
        /// @return this builder
        public Builder add(QuirkHandler q) {
            handlers.add(q);
            return this;
        }

        /// Builds the module applying the handlers.
        ///
        /// The module shares the builder's list of handlers rather than copying it, so add every
        /// handler before building. Every module built here is named `QuirksModule`, and jackson
        /// keeps only one module per name on a mapper (the last one registered): register a single
        /// quirks module per mapper, with all the handlers it needs.
        ///
        /// @return the module, to be registered on a mapper builder
        public SimpleModule build() {
            final var module = new SimpleModule("QuirksModule", Version.unknownVersion());
            module.setDeserializerModifier(new AnnotatedDeserializerModifier(handlers));
            module.setSerializerModifier(new AnnotatedSerializerModifier(handlers));
            return module;
        }
    }
}
