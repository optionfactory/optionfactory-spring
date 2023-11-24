package net.optionfactory.spring.upstream;

import java.net.URI;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public class UpstreamParamTest {

    @Test
    public void canUseUpstreamParam() {
        final URI expected = URI.create("http://example.com/endpoint/value?b=value");
        UpstreamBuilder.create(UpstreamParamClient.class)
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    Assert.assertEquals(expected, uri);
                    return MockClientHttpResponse.okUtf8(MediaType.APPLICATION_JSON, "{}");
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .interceptor(new UpstreamLoggingInterceptor())
                .build()
                .test("value");

    }

}
