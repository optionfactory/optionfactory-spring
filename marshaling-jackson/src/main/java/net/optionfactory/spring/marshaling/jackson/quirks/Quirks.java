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

/**
 * Annotation-driven serialization and deserialization configurations designed
 * to isolate non-standard external API payloads and data formatting quirks.
 */
public interface Quirks {

    /**
     * Maps {@code boolean} or {@link Boolean} fields to strict textual
     * representations instead of standard JSON boolean literals.
     * <p>
     * Example serialization output: {@code "SI"} or {@code "NO"}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Bool {

        /**
         * Text token mapping for {@code true}.
         *
         * @return the token mapping for {@code true}.
         */
        String t() default "SI";

        /**
         * Text token mapping for {@code false}.
         *
         * @return the token mapping for {@code false}.
         */
        String f() default "NO";
    }

    /**
     * Maps a {@link java.time.LocalDate} property to and from an ISO instant
     * string using a target timezone and customizable time or day offsets.
     * <p>
     * Example: {@code 2024-01-02} transforms into
     * {@code "2024-01-02T00:00:00Z"}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalDateAsIsoInstant {

        /**
         * Target timezone identifier for the calculation loop.
         *
         * @return the target timezone.
         */
        String value() default "UTC";

        /**
         * Absolute offset adjustment applied to the intermediate Instant.
         *
         * @return the Instant offset.
         */
        int ioffset() default 0;

        /**
         * Chrono unit used for the intermediate Instant offset calculation.
         *
         * @return the Instant offset chrono unit.
         */
        ChronoUnit iunit() default ChronoUnit.HOURS;

        /**
         * Absolute offset adjustment applied to the base LocalDate.
         *
         * @return the LocalDate offset.
         */
        int ldoffset() default 0;

        /**
         * Chrono unit used for the base LocalDate calculation profile.
         *
         * @return the LocalDate offset chrono unit.
         */
        ChronoUnit ldunit() default ChronoUnit.DAYS;
    }

    /**
     * Maps a {@link java.time.LocalDateTime} property to and from an ISO
     * instant string using a target timezone and customizable time or day
     * offsets.
     * <p>
     * Example: {@code 2024-01-02T12:00:00} transforms into
     * {@code "2024-01-02T12:00:00Z"}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalDateTimeAsIsoInstant {

        /**
         * Target timezone identifier for the calculation loop.
         *
         * @return the target timezone.
         */
        String value() default "UTC";

        /**
         * Absolute offset adjustment applied to the intermediate Instant.
         *
         * @return the instant offset.
         */
        int ioffset() default 0;

        /**
         * Chrono unit used for the intermediate Instant offset calculation.
         *
         * @return the instant offset ChronoUnit.
         */
        ChronoUnit iunit() default ChronoUnit.HOURS;

        /**
         * Absolute offset adjustment applied to the base LocalDateTime.
         *
         * @return the LocalDate offset.
         */
        int ldoffset() default 0;

        /**
         * Chrono unit used for the base LocalDateTime calculation profile.
         *
         * @return the LocalDate offset ChronoUnit.
         */
        ChronoUnit ldunit() default ChronoUnit.DAYS;
    }

    /**
     * Forces standard {@code java.time} properties to format and parse using a
     * pattern string via {@link java.time.format.DateTimeFormatter}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TemporalFormat {

        /**
         * Pattern expression adhering to
         * {@link java.time.format.DateTimeFormatter}.
         *
         * @return the pattern.
         */
        String value();
    }

    /**
     * Formats and parses a {@link java.time.Instant} property strictly as a
     * numeric timestamp block (Epoch time).
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Timestamp {

        /**
         * Evaluates as Unix epoch milliseconds if true; otherwise flags
         * standard seconds.
         *
         * @return if the format is in milliseconds.
         */
        boolean millis() default true;
    }

    /**
     * Explicitly renames a property key to a fixed custom token string value
     * without introducing broad global naming strategies.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Rename {

        /**
         * Targeted key assignment inside the resulting JSON object hierarchy.
         *
         * @return the key.
         */
        String value();
    }

    /**
     * Dynamically converts a camelCase property name into a uppercase
     * {@code SCREAMING_SNAKE_CASE} expression during transport tasks.
     * <p>
     * Example: {@code assignedValue} becomes {@code ASSIGNED_VALUE}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Scream {

    }

    /**
     * Trims leading and trailing whitespace from string values during
     * serialization and deserialization execution windows.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Trim {
    }

    /**
     * Returns a {@link Builder} pre-loaded with all standard, out-of-the-box
     * quirk handlers. Use this to construct a comprehensive connector
     * configuration.
     *
     * @return a pre-configured builder instance
     */
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

    /**
     * Returns an empty {@link Builder} instance. Use this if you want to
     * selectively activate a minimal subset of handlers for a specific mapper
     * profile.
     *
     * @return a fresh builder instance
     */
    public static Builder empty() {
        return new Builder();
    }

    /**
     * Builder pattern used to map activated {@link QuirkHandler} strategies
     * into an isolated Jackson {@link SimpleModule}.
     */
    public static class Builder {

        private final List<QuirkHandler<?>> handlers = new ArrayList<>();

        /**
         * Appends an active quirk handler implementation strategy to the
         * builder stack.
         *
         * @param q target handler registry definition
         * @return the fluent builder reference instance
         */
        public Builder add(QuirkHandler q) {
            handlers.add(q);
            return this;
        }

        /**
         * Bakes the registered quirk handlers into an immutable Jackson module
         * ready for isolated registration onto a target {@code JsonMapper}.
         *
         * @return a configured {@link SimpleModule} instance
         */
        public SimpleModule build() {
            final var module = new SimpleModule("QuirksModule", Version.unknownVersion());
            module.setDeserializerModifier(new AnnotatedDeserializerModifier(handlers));
            module.setSerializerModifier(new AnnotatedSerializerModifier(handlers));
            return module;
        }
    }
}
