package net.optionfactory.spring.upstream.errors;

import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.util.Optional;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public class UpstreamErrorsHandlerJsonTest {

    @Test(expected = RestClientUpstreamException.class)
    public void matchinErrorAnnotationYieldsRestClientUpstreamException() {
        UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
                .requestFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    return MockClientHttpResponse.okUtf8(
                            MediaType.APPLICATION_JSON,
                            """
                            {
                                "metadata": {"success": false},
                                "data": null
                            }
                            """
                    );
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build(ObservationRegistry.NOOP, Optional.empty(), e -> {
                })
                .callWithJsonPath();
    }

    @Test
    public void nonMatchinErrorAnnotationYieldsResult() {
        final var response = UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
                .requestFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    return MockClientHttpResponse.okUtf8(
                            MediaType.APPLICATION_JSON,
                            """
                            {
                                "metadata": {"success": true},
                                "data": null
                            }
                            """
                    );
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build(ObservationRegistry.NOOP, Optional.empty(), e -> {
                })
                .callWithJsonPath();

        Assert.assertEquals(true, response.metadata.success);
    }

}
