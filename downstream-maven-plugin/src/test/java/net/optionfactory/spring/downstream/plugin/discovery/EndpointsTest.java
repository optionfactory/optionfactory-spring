package net.optionfactory.spring.downstream.plugin.discovery;

import io.github.classgraph.ClassGraph;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.downstream.Downstream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EndpointsTest {

    public static class Controller {

        @Downstream.Method
        public void forEveryone() {
        }

        @Downstream.Method(clients = {"web", "mobile"})
        public void forWebAndMobile() {
        }

        @Downstream.Method(clients = "backoffice")
        public void forBackoffice() {
        }

        public void notAnEndpoint() {
        }
    }

    private static Set<String> discover(String targetClientName) {
        try (final var scan = new ClassGraph()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .acceptClasses(Controller.class.getName())
                .scan()) {
            final List<Method> methods = new Endpoints(targetClientName).discover(scan);
            return methods.stream().map(Method::getName).collect(Collectors.toSet());
        }
    }

    @Test
    public void aTargetClientSelectsItsEndpointsAndTheOnesWithoutClients() {
        Assertions.assertEquals(Set.of("forEveryone", "forWebAndMobile"), discover("web"), "endpoints listing the client or no client at all are selected");
    }

    @Test
    public void withoutATargetClientEveryAnnotatedEndpointIsSelected() {
        Assertions.assertEquals(Set.of("forEveryone", "forWebAndMobile", "forBackoffice"), discover(null), "a null target client selects every annotated method, whatever its clients");
    }

    @Test
    public void anUnknownClientOnlySelectsTheEndpointsWithoutClients() {
        Assertions.assertEquals(Set.of("forEveryone"), discover("unknown"), "only endpoints without clients are generated for a client no endpoint lists");
    }
}
