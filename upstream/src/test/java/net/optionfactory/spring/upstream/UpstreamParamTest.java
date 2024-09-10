package net.optionfactory.spring.upstream;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class UpstreamParamTest {

    @Test
    public void canUseUpstreamParam() {
        final URI expected = URI.create("http://example.com/endpoint/value/value");
        UpstreamBuilder.create(UpstreamParamClient.class)
                .requestFactoryMock(c -> {
                    c.response(MediaType.APPLICATION_JSON, "{}");
                    c.responseFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                        Assert.assertEquals(expected, uri);
                        final HttpHeaders h = new HttpHeaders();
                        h.setContentType(MediaType.APPLICATION_JSON);
                        return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), h, new ByteArrayResource("{}".getBytes(StandardCharsets.UTF_8)));
                    });
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .interceptor(new UpstreamLoggingInterceptor(Map.of()))
                .build()
                .testEndpoint("value");

    }

}
