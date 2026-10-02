package net.optionfactory.spring.marshaling.jackson.quirks;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import net.optionfactory.spring.marshaling.jackson.quirks.bool.BooleanQuirkHandler;
import net.optionfactory.spring.marshaling.jackson.quirks.text.TrimQuirkHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.util.NameTransformer;

public class QuirksTest {

    public record Bean(@Quirks.Trim String text, @Quirks.Bool Boolean flag) {

    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Prefixed {

        String value();
    }

    public static class PrefixedQuirkHandler implements QuirkHandler<Prefixed> {

        @Override
        public Class<Prefixed> annotation() {
            return Prefixed.class;
        }

        @Override
        public BeanPropertyWriter serialization(Prefixed ann, BeanPropertyWriter bpw) {
            return bpw.rename(NameTransformer.simpleTransformer(ann.value(), ""));
        }

        @Override
        public SettableBeanProperty deserialization(Prefixed ann, SettableBeanProperty sbp) {
            return sbp.withSimpleName(ann.value() + sbp.getName());
        }
    }

    public record CustomBean(@Prefixed("x_") String name) {

    }

    public record ScreamedAndRenamed(@Quirks.Scream @Quirks.Rename("renamed") String someName) {

    }

    @Test
    public void anEmptyBuilderLeavesTheAnnotationsInert() {
        final var om = JsonMapper.builder().addModule(Quirks.empty().build()).build();
        Assertions.assertEquals("""
                {"text":" a ","flag":true}
                """.trim(), om.writeValueAsString(new Bean(" a ", true)), "annotations without a handler are ignored");
    }

    @Test
    public void onlyTheAddedHandlersAreActive() {
        final var om = JsonMapper.builder().addModule(Quirks.empty().add(new TrimQuirkHandler()).build()).build();
        Assertions.assertEquals("""
                {"text":"a","flag":true}
                """.trim(), om.writeValueAsString(new Bean(" a ", true)), "@Trim applies, @Bool is ignored without its handler");
    }

    @Test
    public void oneModuleCanCarrySeveralHandlers() {
        final var om = JsonMapper.builder().addModule(Quirks.empty().add(new TrimQuirkHandler()).add(new BooleanQuirkHandler()).build()).build();
        Assertions.assertEquals("""
                {"text":"a","flag":"SI"}
                """.trim(), om.writeValueAsString(new Bean(" a ", true)), "both handlers apply");
    }

    @Test
    public void customHandlersApplyBothWays() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().add(new PrefixedQuirkHandler()).build()).build();
        final var json = om.writeValueAsString(new CustomBean("a"));
        Assertions.assertEquals("""
                {"x_name":"a"}
                """.trim(), json, "the custom handler renames the property when serializing");
        Assertions.assertEquals(new CustomBean("a"), om.readValue(json, CustomBean.class), "the custom handler renames the creator property when deserializing");
    }

    @Test
    public void handlersApplyInRegistrationOrder() {
        final var om = JsonMapper.builder().addModule(Quirks.defaults().build()).build();
        final var json = om.writeValueAsString(new ScreamedAndRenamed("a"));
        Assertions.assertEquals("""
                {"renamed":"a"}
                """.trim(), json, "@Rename is registered after @Scream by defaults(), so its name wins");
        Assertions.assertEquals(new ScreamedAndRenamed("a"), om.readValue(json, ScreamedAndRenamed.class), "the same order applies when deserializing");
    }
}
