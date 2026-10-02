package net.optionfactory.spring.downstream.plugin.emit.java;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JavaTypeTranslatorTest {

    public static class Wrapper<T> {

        public T value;
    }

    public record Dto(String name) {

    }

    public static class Outer {

        public static class Inner {
        }
    }

    public static class Fields {

        public LocalDate date;
        public LocalDate[][] dates;
        public Wrapper<Dto> wrapped;
        public List<Dto> list;
        public Outer.Inner inner;
        public String untouched;
    }

    private static String translate(String field, Map<String, String> translations) throws Exception {
        final var registry = new TypeRegistry(Set.of(Dto.class, Outer.class, Outer.Inner.class), "net.generated", Nesting.NESTED);
        return new JavaTypeTranslator(registry, translations).translate(Fields.class.getField(field).getAnnotatedType()).toString();
    }

    @Test
    public void registeredTypesAreReplacedByTheirGeneratedCounterpart() throws Exception {
        Assertions.assertEquals("java.util.List<net.generated.Dto>", translate("list", Map.of()), "type arguments are translated, the raw type is kept");
        Assertions.assertEquals("net.generated.Outer.Inner", translate("inner", Map.of()), "a NESTED type is referenced through its generated outer type");
        Assertions.assertEquals("java.lang.String", translate("untouched", Map.of()), "a type neither registered nor translated is kept");
    }

    @Test
    public void translationsCanTargetPrimitivesAndArrays() throws Exception {
        Assertions.assertEquals("byte[]", translate("date", Map.of(LocalDate.class.getName(), "byte[]")), "an array of a primitive is a valid translation target");
        Assertions.assertEquals("java.lang.String[][]", translate("dates", Map.of(LocalDate.class.getName(), "java.lang.String")), "array components are translated keeping the dimensions");
    }

    @Test
    public void translatingAGenericRawTypeKeepsItsTypeArguments() throws Exception {
        Assertions.assertEquals("com.example.Box<net.generated.Dto>", translate("wrapped", Map.of(Wrapper.class.getName(), "com.example.Box")), "a translated generic class keeps its translated type arguments");
        Assertions.assertEquals("int", translate("wrapped", Map.of(Wrapper.class.getName(), "int")), "a generic class translated to a primitive drops its type arguments");
    }

    @Test
    public void binaryNamesOfNestedTranslationTargetsAreAccepted() throws Exception {
        Assertions.assertEquals("com.example.Outer.Target", translate("date", Map.of(LocalDate.class.getName(), "com.example.Outer$Target")), "a binary nested class name is turned into a nested type reference");
    }
}
