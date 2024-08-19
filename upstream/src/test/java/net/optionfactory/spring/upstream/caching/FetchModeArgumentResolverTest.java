package net.optionfactory.spring.upstream.caching;

import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.caching.FetchModeClient.FetchMode;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.Test;
import org.springframework.http.MediaType;

public class FetchModeArgumentResolverTest {

    @Test
    public void asd() {

        final FetchModeClient client = UpstreamBuilder.create(FetchModeClient.class)
                .requestFactory((ctx, uri, method, headers) -> {
                    return MockClientHttpResponse.okUtf8(MediaType.APPLICATION_JSON, "{}");
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build(ObservationRegistry.NOOP, Optional.empty(), e -> {
                });

        client.get("a", FetchMode.ANY);
    }

}
