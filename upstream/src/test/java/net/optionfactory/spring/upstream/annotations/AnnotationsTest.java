package net.optionfactory.spring.upstream.annotations;

import net.optionfactory.spring.upstream.Upstream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.service.annotation.GetExchange;

public class AnnotationsTest {

    public interface ParentClient {

        @GetExchange("/parent")
        String parent();

        @GetExchange("/parent-annotated-method")
        @Upstream.Logging
        String parentWithMethodLevelLogging();

    }

    @Upstream.Logging
    @Upstream.ErrorOnResponse("true")
    public interface ChildClient extends ParentClient {

    }

    @Upstream("far")
    public interface Far {

    }

    public interface Middle extends Far {

    }

    @Upstream("near")
    public interface Near {

    }

    public interface Root extends Middle, Near {

    }

    @Upstream.ErrorOnResponse(value = "type")
    public interface RepeatableClient {

        @Upstream.ErrorOnResponse(value = "method-1")
        @Upstream.ErrorOnResponse(value = "method-2")
        String annotated();

        String plain();
    }

    @Test
    public void typeLevelAnnotationOnTheProxiedSubinterfaceIsFoundForInheritedMethods() throws Exception {
        final var inherited = ParentClient.class.getMethod("parent");
        Assertions.assertTrue(Annotations.closest(inherited, ChildClient.class, Upstream.Logging.class).isPresent(), "the proxied subinterface annotation must apply to inherited methods");
        Assertions.assertTrue(Annotations.closest(inherited, ParentClient.class, Upstream.Logging.class).isEmpty(), "the declaring interface is not annotated");
    }

    @Test
    public void methodLevelAnnotationWinsOverTypeLevel() throws Exception {
        final var method = ParentClient.class.getMethod("parentWithMethodLevelLogging");
        Assertions.assertTrue(Annotations.closest(method, ChildClient.class, Upstream.Logging.class).isPresent(), "a method level annotation must be found");
    }

    @Test
    public void typeLevelRepeatableOnTheProxiedSubinterfaceIsFoundForInheritedMethods() throws Exception {
        final var inherited = ParentClient.class.getMethod("parent");
        Assertions.assertEquals(1, Annotations.closestRepeatable(inherited, ChildClient.class, Upstream.ErrorOnResponse.class).size(), "the proxied subinterface repeatable must apply to inherited methods");
        Assertions.assertEquals(0, Annotations.closestRepeatable(inherited, ParentClient.class, Upstream.ErrorOnResponse.class).size(), "the declaring interface carries no repeatable");
    }

    @Test
    public void superInterfacesAreSearchedBreadthFirst() {
        final var got = Annotations.closest(Root.class, Upstream.class).orElseThrow();
        Assertions.assertEquals("near", got.value(), "a direct super-interface must win over a farther ancestor declared first");
    }

    @Test
    public void missingAnnotationYieldsEmpty() {
        Assertions.assertTrue(Annotations.closest(Middle.class, Upstream.Logging.class).isEmpty(), "an annotation found nowhere in the hierarchy must yield empty");
        Assertions.assertTrue(Annotations.closestRepeatable(Middle.class, Upstream.ErrorOnResponse.class).isEmpty(), "a repeatable found nowhere in the hierarchy must yield an empty list");
    }

    @Test
    public void methodRepeatablesReplaceTypeLevelOnesInsteadOfMerging() throws Exception {
        final var got = Annotations.closestRepeatable(RepeatableClient.class.getMethod("annotated"), Upstream.ErrorOnResponse.class);
        Assertions.assertEquals(2, got.size(), "method level repeatables must not be merged with type level ones");
        Assertions.assertEquals("method-1", got.get(0).value(), "repeatables must keep their declaration order");
        Assertions.assertEquals("method-2", got.get(1).value(), "every repeated method level annotation must be returned");
    }

    @Test
    public void typeLevelRepeatableAppliesToUnannotatedMethods() throws Exception {
        final var got = Annotations.closestRepeatable(RepeatableClient.class.getMethod("plain"), Upstream.ErrorOnResponse.class);
        Assertions.assertEquals(1, got.size(), "the type level annotation must apply to a method without its own");
        Assertions.assertEquals("type", got.get(0).value(), "the type level annotation must be the one returned");
    }

    @Test
    public void declaringInterfaceIsTheDefaultRoot() throws Exception {
        final var inherited = ParentClient.class.getMethod("parent");
        Assertions.assertTrue(Annotations.closest(inherited, Upstream.Logging.class).isEmpty(), "without a root, only the declaring interface hierarchy must be searched");
        Assertions.assertTrue(Annotations.closest(ParentClient.class.getMethod("parentWithMethodLevelLogging"), Upstream.Logging.class).isPresent(), "the method itself must still be searched");
    }
}
