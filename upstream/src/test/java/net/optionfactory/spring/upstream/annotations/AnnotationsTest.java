package net.optionfactory.spring.upstream.annotations;

import java.util.List;
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

    @Upstream.Logging(requestMaxSize = 1)
    @Upstream.ErrorOnResponse("first")
    public interface FirstSibling {

        @GetExchange("/first")
        String first();
    }

    @Upstream.Logging(requestMaxSize = 2)
    @Upstream.ErrorOnResponse("second")
    public interface SecondSibling {

        @GetExchange("/second")
        String second();
    }

    public interface UnannotatedSibling {

        @GetExchange("/unannotated")
        String unannotated();
    }

    public interface Aggregate extends FirstSibling, SecondSibling, UnannotatedSibling {

    }

    @Upstream.Logging(requestMaxSize = 1)
    @Upstream.ErrorOnResponse("ancestor")
    public interface Ancestor {

    }

    @Upstream.Logging(requestMaxSize = 2)
    @Upstream.ErrorOnResponse("declaring")
    public interface Declaring extends Ancestor {

        @GetExchange("/declared")
        String declared();
    }

    public interface ListingTheAncestorFirst extends Ancestor, Declaring {

    }

    @Upstream.ErrorOnResponse("type")
    public interface Redeclared {

        @GetExchange("/redeclared")
        @Upstream.Logging(requestMaxSize = 3)
        @Upstream.ErrorOnResponse("method-1")
        @Upstream.ErrorOnResponse("method-2")
        @Upstream.Header(key = "inherited", value = "'1'")
        Object get();
    }

    public interface Redeclaring extends Redeclared {

        @Override
        String get();
    }

    public interface RedeclaringWithItsOwn extends Redeclared {

        @Override
        @Upstream.Header(key = "own", value = "'2'")
        String get();
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
    public void siblingInterfacesNeverConfigureEachOthersEndpoints() throws Exception {
        final var second = SecondSibling.class.getMethod("second");
        Assertions.assertEquals(2, Annotations.closest(second, Aggregate.class, Upstream.Logging.class).orElseThrow().requestMaxSize(), "an endpoint must use its declaring interface annotation, not the one of a sibling declared first");
        Assertions.assertEquals("second", Annotations.closestRepeatable(second, Aggregate.class, Upstream.ErrorOnResponse.class).get(0).value(), "an endpoint must use its declaring interface repeatables, not the ones of a sibling declared first");
        final var unannotated = UnannotatedSibling.class.getMethod("unannotated");
        Assertions.assertTrue(Annotations.closest(unannotated, Aggregate.class, Upstream.Logging.class).isEmpty(), "an endpoint of an unannotated interface must not borrow a sibling annotation");
        Assertions.assertTrue(Annotations.closestRepeatable(unannotated, Aggregate.class, Upstream.ErrorOnResponse.class).isEmpty(), "an endpoint of an unannotated interface must not borrow sibling repeatables");
    }

    @Test
    public void declaringInterfaceWinsOverItsAncestorsListedByTheProxiedOne() throws Exception {
        final var declared = Declaring.class.getMethod("declared");
        Assertions.assertEquals(2, Annotations.closest(declared, ListingTheAncestorFirst.class, Upstream.Logging.class).orElseThrow().requestMaxSize(), "the declaring interface annotation must win over its ancestor's, even when the proxied interface lists the ancestor first");
        Assertions.assertEquals("declaring", Annotations.closestRepeatable(declared, ListingTheAncestorFirst.class, Upstream.ErrorOnResponse.class).get(0).value(), "the declaring interface repeatables must win over its ancestor's, even when the proxied interface lists the ancestor first");
    }

    @Test
    public void redeclaredMethodsKeepTheMethodLevelAnnotationsOfTheDeclarationTheyOverride() throws Exception {
        final var redeclared = Redeclaring.class.getMethod("get");
        Assertions.assertEquals(3, Annotations.closest(redeclared, Redeclaring.class, Upstream.Logging.class).orElseThrow().requestMaxSize(), "a redeclared method must keep the method level annotation of the overridden declaration");
        Assertions.assertEquals(List.of("method-1", "method-2"), Annotations.closestRepeatable(redeclared, Redeclaring.class, Upstream.ErrorOnResponse.class).stream().map(a -> a.value()).toList(), "a redeclared method must keep the method level repeatables of the overridden declaration, in order, over the type level ones");
        Assertions.assertEquals(List.of("inherited"), Annotations.onMethodRepeatable(redeclared, Upstream.Header.class).stream().map(a -> a.key()).toList(), "a redeclared method must keep the method level repeatables of the overridden declaration");
        Assertions.assertEquals(Redeclared.class, Annotations.declaration(redeclared, Upstream.Header.class).orElseThrow().getDeclaringClass(), "the declaration carrying the annotations must be the overridden one");
    }

    @Test
    public void redeclaredMethodsWithTheirOwnAnnotationsReplaceTheInheritedOnes() throws Exception {
        final var redeclared = RedeclaringWithItsOwn.class.getMethod("get");
        Assertions.assertEquals(List.of("own"), Annotations.onMethodRepeatable(redeclared, Upstream.Header.class).stream().map(a -> a.key()).toList(), "the redeclaration's own repeatables must replace the inherited ones as a whole");
        Assertions.assertTrue(Annotations.onMethod(redeclared, Upstream.SoapAction.class).isEmpty(), "an annotation declared nowhere on the method hierarchy must yield empty");
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
