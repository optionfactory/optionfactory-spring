package net.optionfactory.spring.marshaling.jackson.quirks.text;

import java.util.Arrays;
import java.util.stream.Collectors;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

public class ScreamQuirkHandler implements QuirkHandler<Quirks.Scream> {
    private final CamelCaseToSnakeCase transformer = new CamelCaseToSnakeCase();

    @Override
    public Class<Quirks.Scream> annotation() {
        return Quirks.Scream.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Scream ann, BeanPropertyWriter bpw) {
        return bpw.rename(transformer);
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Scream ann, SettableBeanProperty sbp) {
        final var newName = transformer.transform(sbp.getName());
        return sbp.withSimpleName(newName);

    }

    public static class CamelCaseToSnakeCase extends NameTransformer {

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

        @Override
        public String reverse(@NonNull String transformed) {
            final var s = transformed.toLowerCase();
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
