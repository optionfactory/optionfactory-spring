package net.optionfactory.spring.downstream.plugin.emit.ts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry;
import net.optionfactory.spring.downstream.plugin.mapping.TypeRegistry.Nesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TypeScriptTypeTranslatorTest {

    public enum Color {
        RED
    }

    public enum Unregistered {
        X
    }

    public record Money(BigDecimal amount) {

    }

    public static class Outer {

        public static class Inner {
        }
    }

    public static class Fields<T extends Number> {

        public List<String> list;
        public Set<Money> set;
        public Map<Color, Integer> byEnum;
        public Map<LocalDate, Integer> byDate;
        public Map<Long, Integer> byLong;
        public Optional<Money> optional;
        public List<? extends Money> wildcard;
        public List<?> unbounded;
        public T variable;
        public List<T>[] genericArray;
        public int[] primitives;
        public Unregistered unregistered;
        public LocalDate date;
        public Object object;
        public char character;
        public Void nothing;
        public Outer.Inner inner;
    }

    private static String translate(String field, Map<String, String> translations, Map<String, String> aliases) throws Exception {
        final var registry = new TypeRegistry(Set.of(Color.class, Money.class, Outer.class, Outer.Inner.class), "", Nesting.NESTED);
        final var translator = new TypeScriptTypeTranslator(registry, translations, aliases);
        return translator.translate(Fields.class.getField(field).getGenericType());
    }

    private static String translate(String field) throws Exception {
        return translate(field, Map.of(), Map.of());
    }

    @Test
    public void collectionsBecomeArraysAndOptionalsAreUnwrapped() throws Exception {
        Assertions.assertEquals("string[]", translate("list"), "a List becomes an array of its element type");
        Assertions.assertEquals("Money[]", translate("set"), "any Collection becomes an array of its element type");
        Assertions.assertEquals("Money", translate("optional"), "an Optional becomes its content type");
    }

    @Test
    public void mapKeysThatCannotIndexARecordFallBackToString() throws Exception {
        Assertions.assertEquals("Record<Color, number>", translate("byEnum"), "an enum key is kept");
        Assertions.assertEquals("Record<number, number>", translate("byLong"), "a number key is kept");
        Assertions.assertEquals("Record<string, number>", translate("byDate"), "a key translating to neither string, number, an alias nor an enum becomes string");
    }

    @Test
    public void wildcardsAndTypeVariablesAreResolved() throws Exception {
        Assertions.assertEquals("Money[]", translate("wildcard"), "a bounded wildcard becomes its upper bound");
        Assertions.assertEquals("any[]", translate("unbounded"), "an unbounded wildcard becomes any");
        Assertions.assertEquals("T", translate("variable"), "a type variable keeps its name");
        Assertions.assertEquals("T[][]", translate("genericArray"), "a generic array becomes an array of the translated component");
        Assertions.assertEquals("number[]", translate("primitives"), "a primitive array becomes an array of number");
    }

    @Test
    public void unknownTypesBecomeAnyButEnumsKeepTheirName() throws Exception {
        Assertions.assertEquals("any", translate("date"), "a class neither registered nor known nor translated becomes any");
        Assertions.assertEquals("any", translate("object"), "Object becomes any");
        Assertions.assertEquals("Unregistered", translate("unregistered"), "an enum outside the registry is referenced by its simple name");
        Assertions.assertEquals("string", translate("character"), "a char becomes string");
        Assertions.assertEquals("void", translate("nothing"), "Void becomes void");
    }

    @Test
    public void registeredNestedTypesAreReferencedByTheirFlatName() throws Exception {
        Assertions.assertEquals("OuterInner", translate("inner"), "TypeScript has no nested types: the registered names are joined");
    }

    @Test
    public void translationsToUnknownTypesUseTheirSimpleName() throws Exception {
        Assertions.assertEquals("string", translate("date", Map.of(LocalDate.class.getName(), String.class.getName()), Map.of()), "a translation to a known java type uses its TypeScript type");
        Assertions.assertEquals("IsoDate", translate("date", Map.of(LocalDate.class.getName(), "com.example.IsoDate"), Map.of()), "a translation to an unknown class uses its simple name");
    }

    @Test
    public void aliasedTypesAreReferencedByTheAliasName() throws Exception {
        Assertions.assertEquals("LocalDate", translate("date", Map.of(), Map.of(LocalDate.class.getName(), "string")), "an aliased type is referenced by its simple name, the alias being declared in the spec");
        Assertions.assertEquals("Record<LocalDate, number>", translate("byDate", Map.of(), Map.of(LocalDate.class.getName(), "string")), "an alias is a valid record key");
    }

    @Test
    public void aTranslationToAnAliasedClassReferencesTheAlias() throws Exception {
        Assertions.assertEquals("IsoDate", translate("date", Map.of(LocalDate.class.getName(), "com.example.IsoDate"), Map.of("com.example.IsoDate", "string")), "a translation targeting an aliased class references the declared alias, not the translated class");
    }

    @Test
    public void translationTargetsAreMappedToTypeScript() throws Exception {
        Assertions.assertEquals("number[]", translate("date", Map.of(LocalDate.class.getName(), "byte[]"), Map.of()), "an array of a java primitive becomes an array of its TypeScript type");
        Assertions.assertEquals("string[][]", translate("date", Map.of(LocalDate.class.getName(), "java.lang.String[][]"), Map.of()), "a multidimensional array target is mapped component by component");
        Assertions.assertEquals("Money[]", translate("date", Map.of(LocalDate.class.getName(), Money.class.getName() + "[]"), Map.of()), "an array of a payload becomes an array of its generated name");
        Assertions.assertEquals("number", translate("date", Map.of(LocalDate.class.getName(), "long"), Map.of()), "a java primitive becomes its TypeScript type");
    }

    @Test
    public void simpleNameStripsPackagesAndOuterClasses() {
        Assertions.assertEquals("Inner", TypeScriptTypeTranslator.simpleName("com.example.Outer$Inner"), "both the package and the binary outer class are stripped");
        Assertions.assertEquals("Plain", TypeScriptTypeTranslator.simpleName("Plain"), "a name without package is returned as is");
        Assertions.assertNull(TypeScriptTypeTranslator.simpleName(null), "a null name stays null");
    }
}
