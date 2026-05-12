package net.optionfactory.spring.upstream.caching;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.caching.FetchModeClient.FetchMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

public class FetchModeArgumentResolverTest {

    @Test
    public void canPassFetchMode() {

        final FetchModeClient client = UpstreamBuilder.create(FetchModeClient.class)
                .requestFactoryMock(c -> {
                    c.response(MediaType.APPLICATION_JSON, "{}");
                })
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();

        client.get("a", FetchMode.ANY);
    }

}
