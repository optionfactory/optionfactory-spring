package net.optionfactory.spring.marshaling.jackson.quirks.text;

import java.util.Arrays;
import java.util.stream.Collectors;
import net.optionfactory.spring.marshaling.jackson.quirks.QuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.Quirks;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

public class ScreamQuirkHandler implements QuirkHandler<Quirks.Scream> {

    @Override
    public Class<Quirks.Scream> annotation() {
        return Quirks.Scream.class;
    }

    @Override
    public BeanPropertyWriter serialization(Quirks.Scream ann, BeanPropertyWriter bpw) {
        return bpw.rename(new CamelCaseToSnakeCase());
    }

    @Override
    public SettableBeanProperty deserialization(Quirks.Scream ann, SettableBeanProperty sbp) {
        final var newName = new CamelCaseToSnakeCase().transform(sbp.getName());
        return sbp.withSimpleName(newName);

    }

    public class CamelCaseToSnakeCase extends NameTransformer {

        @Override
        public String transform(String camel) {
            final java.lang.StringBuilder result = new StringBuilder();
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
        public String reverse(String transformed) {
            final java.lang.String s = transformed.toLowerCase();
            if (!s.contains("_")) {
                return s;
            }
            final java.lang.String prefix = s.substring(0, s.indexOf("_"));
            final java.lang.String suffix = Arrays.stream(s.substring(s.indexOf("_") + 1).split("_")).map(s1 -> Character.toUpperCase(s1.charAt(0)) + s1.substring(1)).collect(Collectors.joining());
            return prefix + suffix;
        }

    }

}
