package net.optionfactory.spring.downstream.plugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.optionfactory.spring.downstream.Downstream;
import net.optionfactory.spring.downstream.plugin.discovery.Payloads;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PayloadsTest {

    public static class RecursiveDataStruct {

        public String name;
        public List<RecursiveDataStruct> children;
    }

    public static class DeepGenericDto {
    }

    public static class EndpointHolder {

        @Downstream.Method
        public List<Map<String, DeepGenericDto>> endpointWithGenericType() {
            return null;
        }

        @Downstream.Method
        public RecursiveDataStruct endpointWithRecursiveDataStructure() {
            return null;
        }
    }

    @Test
    public void shouldExtractDeeplyNestedGenericsFromMethodSignatures() throws Exception {
        final var traverser = new Payloads("net.optionfactory");
        final var method = EndpointHolder.class.getMethod("endpointWithGenericType");
        final var payloads = traverser.discover(List.of(method));
        Assertions.assertTrue(payloads.contains(DeepGenericDto.class), "payloads nested deep in generic arguments are discovered");
    }

    @Test
    public void shouldHaltGracefullyOnCyclicGraphDependencies() throws Exception {
        final var traverser = new Payloads("net.optionfactory");
        final var method = EndpointHolder.class.getMethod("endpointWithRecursiveDataStructure");
        Assertions.assertDoesNotThrow(() -> {
            final Set<Class<?>> payloads = traverser.discover(List.of(method));
            Assertions.assertTrue(payloads.contains(RecursiveDataStruct.class), "a self referencing payload is discovered");
        }, "a cyclic payload graph is walked once, without recursing forever");
    }
}
