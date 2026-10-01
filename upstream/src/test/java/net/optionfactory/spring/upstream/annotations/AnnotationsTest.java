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

    @Test
    public void typeLevelAnnotationOnTheProxiedSubinterfaceIsFoundForInheritedMethods() throws Exception {
        final var inherited = ParentClient.class.getMethod("parent");
        Assertions.assertTrue(Annotations.closest(inherited, ChildClient.class, Upstream.Logging.class).isPresent());
        Assertions.assertTrue(Annotations.closest(inherited, ParentClient.class, Upstream.Logging.class).isEmpty());
    }

    @Test
    public void methodLevelAnnotationWinsOverTypeLevel() throws Exception {
        final var method = ParentClient.class.getMethod("parentWithMethodLevelLogging");
        Assertions.assertTrue(Annotations.closest(method, ChildClient.class, Upstream.Logging.class).isPresent());
    }

    @Test
    public void typeLevelRepeatableOnTheProxiedSubinterfaceIsFoundForInheritedMethods() throws Exception {
        final var inherited = ParentClient.class.getMethod("parent");
        Assertions.assertEquals(1, Annotations.closestRepeatable(inherited, ChildClient.class, Upstream.ErrorOnResponse.class).size());
        Assertions.assertEquals(0, Annotations.closestRepeatable(inherited, ParentClient.class, Upstream.ErrorOnResponse.class).size());
    }
}
