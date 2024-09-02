package net.optionfactory.spring.upstream.caching;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.caching.FetchModeClient.FetchMode;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.Test;
import org.springframework.http.MediaType;

public class FetchModeArgumentResolverTest {

    @Test
    public void asd() {

        final FetchModeClient client = UpstreamBuilder.create(FetchModeClient.class)
                .requestFactoryMock(c -> {
                    c.response(MockClientHttpResponse.okUtf8(MediaType.APPLICATION_JSON, "{}"));
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build();

        client.get("a", FetchMode.ANY);
    }

}
