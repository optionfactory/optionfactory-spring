package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.MediaType;

public class UpstreamErrorsHandlerJsonTest {

    @Test(expected = RestClientUpstreamException.class)
    public void matchinErrorAnnotationYieldsRestClientUpstreamException() {
        UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
                .requestFactoryMock(c -> {
                    c.response(MediaType.APPLICATION_JSON,
                            """
                            {
                                "metadata": {"success": false},
                                "data": null
                            }
                            """
                    );
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build()
                .callWithJsonPath();
    }

    @Test
    public void nonMatchinErrorAnnotationYieldsResult() {
        final var response = UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
                .requestFactoryMock(c -> {
                    c.response(MediaType.APPLICATION_JSON,
                            """
                            {
                                "metadata": {"success": true},
                                "data": null
                            }
                            """
                    );
                })
                .restClient(r -> r.baseUrl("http://example.com"))
                .build()
                .callWithJsonPath();

        Assert.assertEquals(true, response.metadata.success);
    }

}
