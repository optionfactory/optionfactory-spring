package net.optionfactory.spring.downstream.plugin;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.reflection.Reflection;
import net.optionfactory.spring.downstream.plugin.reflection.Reflection.CandidateField;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReflectionTest {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotNull {

    }

    public static class Base {

        public String inherited;
    }

    public static class Derived extends Base {

        public static String constant;
        public transient String notSerialized;
        protected String notPublic;
        public String own;
        @Downstream.Ignore
        public String ignored;
    }

    public record IgnoringRecord(String kept, @Downstream.Ignore String skipped) {

    }

    public static class IgnoringBean {

        public String getVisible() {
            return null;
        }

        @Downstream.Ignore
        public String getHidden() {
            return null;
        }
    }

    public static class Bean {

        private String name;
        private boolean active;
        private Boolean boxedFlag;
        private int count;

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }

        public Boolean isBoxedFlag() {
            return boxedFlag;
        }

        public int isCount() {
            return count;
        }

        public String get() {
            return null;
        }

        public String compute(int x) {
            return null;
        }

        public static String getStatic() {
            return null;
        }

        String getPackagePrivate() {
            return null;
        }
    }

    public static class NullabilityBean {

        @Nullable
        private String fromField;

        public String getFromField() {
            return fromField;
        }

        public @Nullable String fromTypeUse;
        @NotNull
        public String required;
        public Optional<String> maybe;
    }

    public record RecordPayload(@Nullable String a, Optional<Integer> b) {

        public String getDerived() {
            return null;
        }
    }

    private static List<String> names(List<CandidateField> fields) {
        return fields.stream().map(CandidateField::name).toList();
    }

    private static CandidateField field(List<CandidateField> fields, String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    public void superclassesAreListedFromTheRootDownExcludingTheStopClass() {
        final var classes = Reflection.superclasses(Derived.class, Object.class);
        Assertions.assertEquals(List.of(Base.class, Derived.class), List.copyOf(classes), "hierarchy goes root first, stop class excluded");
    }

    @Test
    public void superclassesStopAtTheGivenClass() {
        final var classes = Reflection.superclasses(Derived.class, Base.class);
        Assertions.assertEquals(List.of(Derived.class), List.copyOf(classes), "the stop class and its ancestors are excluded");
    }

    @Test
    public void onlyPublicInstanceNonTransientNonIgnoredFieldsAreCandidatesSuperclassFirst() {
        final var fields = Reflection.candidateFields(Derived.class, Object.class);
        Assertions.assertEquals(List.of("inherited", "own"), names(fields), "static, transient, non public and ignored fields are skipped, inherited fields come first");
    }

    @Test
    public void gettersBecomePropertiesFollowingTheBeanConventions() {
        final var fields = Reflection.candidateFields(Bean.class, Object.class);
        final var names = fields.stream().map(CandidateField::name).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("name", "active", "boxedFlag"), names, "getX and boolean or Boolean isX are properties; bare get, non boolean isX, methods with parameters, static and non public getters are not");
    }

    @Test
    public void nullabilityIsDetectedOnTheBackingFieldOfAGetterAndOnTypeUses() {
        final var fields = Reflection.candidateFields(NullabilityBean.class, Object.class);
        Assertions.assertTrue(field(fields, "fromField").nullable(), "@Nullable on the private backing field marks the getter property nullable");
        Assertions.assertTrue(field(fields, "fromTypeUse").nullable(), "a type-use @Nullable marks the field nullable");
        Assertions.assertTrue(field(fields, "required").nonNull(), "annotations are recognised by simple name: any @NotNull marks the field non null");
        Assertions.assertFalse(field(fields, "required").nullable(), "a @NotNull field is not nullable");
        Assertions.assertTrue(field(fields, "maybe").optional(), "an Optional field is flagged as optional");
    }

    @Test
    public void recordsExposeTheirComponentsOnlyInDeclarationOrder() {
        final var fields = Reflection.candidateFields(RecordPayload.class, Object.class);
        Assertions.assertEquals(List.of("a", "b"), names(fields), "records expose their components only, getters are not looked at");
        Assertions.assertTrue(field(fields, "a").nullable(), "a @Nullable component is nullable");
        Assertions.assertTrue(field(fields, "b").optional(), "an Optional component is optional");
    }

    @Test
    public void ignoredRecordComponentsAreSkipped() {
        final var fields = Reflection.candidateFields(IgnoringRecord.class, Object.class);
        Assertions.assertEquals(List.of("kept"), names(fields), "a record component annotated with @Downstream.Ignore is not a property");
    }

    @Test
    public void ignoredGettersAreSkipped() {
        final var fields = Reflection.candidateFields(IgnoringBean.class, Object.class);
        Assertions.assertEquals(List.of("visible"), names(fields), "a getter annotated with @Downstream.Ignore is not a property");
    }
}
