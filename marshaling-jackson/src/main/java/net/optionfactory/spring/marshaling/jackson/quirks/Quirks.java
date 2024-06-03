package net.optionfactory.spring.marshaling.jackson.quirks;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.marshaling.jackson.quirks.adapters.AnnotatedDeserializerModifier;
import net.optionfactory.spring.marshaling.jackson.quirks.adapters.AnnotatedSeralizerModifier;
import net.optionfactory.spring.marshaling.jackson.quirks.bool.BooleanQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.text.ScreamQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.text.TrimQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.time.LocalDateAsIsoInstantQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.time.TemporalFormatQuirkHandler;

public interface Quirks {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Bool {

        String t() default "SI";

        String f() default "NO";
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface LocalDateAsIsoInstant {

        String value() default "UTC";

        int ioffset() default 0;

        ChronoUnit iunit() default ChronoUnit.HOURS;

        int ldoffset() default 0;

        ChronoUnit ldunit() default ChronoUnit.DAYS;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface TemporalFormat {

        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Scream {

    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Trim {
    }

    public static Builder defaults() {
        return new Builder()
                .add(new BooleanQuirkHandler())
                .add(new LocalDateAsIsoInstantQuirkHandler())
                .add(new TemporalFormatQuirkHandler())
                .add(new ScreamQuirkHandler())
                .add(new TrimQuirkHandler());

    }

    public static Builder empty() {
        return new Builder();
    }

    public static class Builder {

        private final List<QuirkHandler> handlers = new ArrayList<>();

        public Builder add(QuirkHandler q) {
            handlers.add(q);
            return this;
        }

        public SimpleModule build() {
            final var module = new SimpleModule("QuirksModule", Version.unknownVersion());
            module.setDeserializerModifier(new AnnotatedDeserializerModifier(handlers));
            module.setSerializerModifier(new AnnotatedSeralizerModifier(handlers));
            return module;
        }
    }
}
