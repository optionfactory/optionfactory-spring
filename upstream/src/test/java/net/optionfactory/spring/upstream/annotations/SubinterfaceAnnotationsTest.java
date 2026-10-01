package net.optionfactory.spring.upstream.annotations;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;

public class SubinterfaceAnnotationsTest {

    public interface ParentClient {

        @GetExchange("/parent")
        @Upstream.Endpoint("parent-endpoint")
        java.util.Map<String, String> parent();

    }

    @Upstream("subinterface-client")
    @Upstream.AlertOnResponse("true")
    public interface ChildClient extends ParentClient {

    }

    @Test
    public void typeLevelAlertOnTheProxiedSubinterfaceFiresForInheritedEndpoints() {
        final var events = new CopyOnWriteArrayList<Object>();
        final ApplicationEventPublisher publisher = events::add;
        final var client = UpstreamBuilder.create(ChildClient.class)
                .requestFactoryMock(c -> {
                    c.response(MediaType.APPLICATION_JSON, "{}");
                })
                .json(tools.jackson.databind.json.JsonMapper.builder().build())
                .publisher(publisher)
                .build();
        Assertions.assertTrue(client.parent().isEmpty());
        Assertions.assertEquals(1, events.stream().filter(e -> e instanceof UpstreamAlertEvent).count(),
                "an @Upstream.AlertOnResponse declared on the proxied subinterface must configure inherited endpoints");
    }

}
