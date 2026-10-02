package net.optionfactory.spring.downstream.plugin.discovery;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.Downstream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PayloadsDiscoveryTest {

    public record Item(String name, Detail detail) {

    }

    public record Detail(Kind kind) {

    }

    public enum Kind {
        A, B;

        public static class InsideEnum {
        }
    }

    public static class Container {

        public static class NeverReferenced {
        }
    }

    public record Request(String text) {

    }

    public record Skipped(Item item) {

    }

    @Downstream.Ignore
    public record IgnoredType(Item item) {

    }

    public interface Api {

        @Downstream.Method
        Item[][] arrays();

        @Downstream.Method
        Map<String, List<Item>> generics();

        @Downstream.Method
        Container container();

        @Downstream.Method
        String outside(Request request, @Downstream.Ignore Skipped skipped, Principal principal);

        @Downstream.Method
        IgnoredType ignored();
    }

    private static Set<Class<?>> discover(String methodName) throws Exception {
        final var method = List.of(Api.class.getMethods()).stream().filter(m -> m.getName().equals(methodName)).findFirst().orElseThrow();
        return new Payloads(PayloadsDiscoveryTest.class.getPackageName()).discover(List.of(method));
    }

    @Test
    public void arrayComponentsAndFieldTypesAreWalkedRecursively() throws Exception {
        Assertions.assertEquals(Set.of(Item.class, Detail.class, Kind.class), discover("arrays"), "multidimensional arrays are unwrapped and field types are followed down to the enum");
    }

    @Test
    public void typeArgumentsAreWalkedAndTypesOutsideTheSourcePackageAreLeftOut() throws Exception {
        Assertions.assertEquals(Set.of(Item.class, Detail.class, Kind.class), discover("generics"), "Map, List and String are outside the source package, their type arguments are walked");
    }

    @Test
    public void nestedClassesOfAPayloadAreIncludedEvenWhenNotReferenced() throws Exception {
        Assertions.assertEquals(Set.of(Container.class, Container.NeverReferenced.class), discover("container"), "the declared classes of a payload are payloads too");
    }

    @Test
    public void nestedClassesOfAnEnumAreNotWalked() throws Exception {
        Assertions.assertFalse(discover("arrays").contains(Kind.InsideEnum.class), "enums are leaves: their nested classes are not walked");
    }

    @Test
    public void ignoredParametersAreNotWalked() throws Exception {
        Assertions.assertEquals(Set.of(Request.class), discover("outside"), "the @Downstream.Ignore parameter and the types outside the source package are left out");
    }

    @Test
    public void ignoredTypesAreNeitherCollectedNorWalked() throws Exception {
        Assertions.assertEquals(Set.of(), discover("ignored"), "an @Downstream.Ignore type is not collected and its fields are not walked");
    }

    @Test
    public void theSourcePackageMatchesWholePackageNamesOnly() throws Exception {
        final var method = Api.class.getMethod("arrays");
        final var packageName = PayloadsDiscoveryTest.class.getPackageName();
        final var truncated = packageName.substring(0, packageName.length() - 1);
        Assertions.assertEquals(Set.of(), new Payloads(truncated).discover(List.of(method)), "a source package does not match a package whose last segment merely starts with it");
        final var parent = packageName.substring(0, packageName.lastIndexOf('.'));
        Assertions.assertEquals(Set.of(Item.class, Detail.class, Kind.class), new Payloads(parent).discover(List.of(method)), "a source package matches its subpackages");
        Assertions.assertEquals(Set.of(Item.class, Detail.class, Kind.class), new Payloads(parent + ".").discover(List.of(method)), "a source package ending with a dot matches its subpackages");
    }
}
