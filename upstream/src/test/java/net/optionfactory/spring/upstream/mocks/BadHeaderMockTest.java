package net.optionfactory.spring.upstream.mocks;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

public class BadHeaderMockTest {

    @Test
    public void malformedHeaderLineWithoutColonYieldsClearError() {
        final var client = UpstreamBuilder.create(BadHeaderMockClient.class)
                .requestFactoryMock(c -> {
                })
                .json(JsonMapper.builder().build())
                .baseUri("http://example.com")
                .build();
        Assertions.assertThrows(RestClientException.class, client::call);
    }
}
