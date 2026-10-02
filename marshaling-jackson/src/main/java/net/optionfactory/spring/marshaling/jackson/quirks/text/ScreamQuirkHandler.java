package net.optionfactory.spring.marshaling.jackson.quirks.text;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

/// Handles [Quirks.Scream], through [CamelCaseToSnakeCase].
public class ScreamQuirkHandler implements QuirkHandler<Quirks.Scream> {
    private final CamelCaseToSnakeCase transformer = new CamelCaseToSnakeCase();

    /// @return [Quirks.Scream]
    @Override
    public Class<Quirks.Scream> annotation() {
        return Quirks.Scream.class;
    }

    /// @param ann the annotation
    /// @param bpw the writer of the property
    /// @return a copy of the writer under the SCREAMING_SNAKE_CASE name
    @Override
    public BeanPropertyWriter serialization(Quirks.Scream ann, BeanPropertyWriter bpw) {
        return bpw.rename(transformer);
    }

    /// @param ann the annotation
    /// @param sbp the property
    /// @return a copy of the property under the SCREAMING_SNAKE_CASE name
    @Override
    public SettableBeanProperty deserialization(Quirks.Scream ann, SettableBeanProperty sbp) {
        final var newName = transformer.transform(sbp.getName());
        return sbp.withSimpleName(newName);

    }

    /// Converts between camelCase and SCREAMING_SNAKE_CASE names.
    ///
    /// The conversion is lossy, so [#reverse] is the inverse of [#transform] only for names made of
    /// lowercase words, each but the first one capitalized (`mySuperVariable`).
    public static class CamelCaseToSnakeCase extends NameTransformer {

        /// Uppercases the name, putting an underscore before every uppercase letter but the first
        /// character: `myVariable` is `MY_VARIABLE`, `userID` is `USER_I_D`.
        ///
        /// @param camel the camelCase name
        /// @return the SCREAMING_SNAKE_CASE name
        @Override
        public String transform(@NonNull String camel) {
            final var result = new StringBuilder();
            for (int i = 0; i != camel.length(); i++) {
                char ch = camel.charAt(i);
                if (Character.isUpperCase(ch) && i > 0) {
                    result.append('_');
                }
                result.append(Character.toUpperCase(ch));
            }
            return result.toString();
        }

        /// Lowercases the name and capitalizes every word after the first, dropping the
        /// underscores, repeated ones included: `MY__VARIABLE` is `myVariable`. A leading
        /// underscore makes the first word empty, so the name starts with an uppercase letter
        /// (`_MY_VARIABLE` is `MyVariable`).
        ///
        /// @param transformed the SCREAMING_SNAKE_CASE name
        /// @return the camelCase name
        @Override
        public String reverse(@NonNull String transformed) {
            final var s = transformed.toLowerCase(Locale.ROOT);
            if (!s.contains("_")) {
                return s;
            }
            final var prefix = s.substring(0, s.indexOf("_"));
            final var suffix = Arrays.stream(s.substring(s.indexOf("_") + 1).split("_"))
                    .filter(part -> !part.isEmpty())                    
                    .map(s1 -> Character.toUpperCase(s1.charAt(0)) + s1.substring(1))
                    .collect(Collectors.joining());
            return prefix + suffix;
        }

    }

}
