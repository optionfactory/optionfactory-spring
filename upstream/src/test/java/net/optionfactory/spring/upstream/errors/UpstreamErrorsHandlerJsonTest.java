package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class UpstreamErrorsHandlerJsonTest {

    @Test
    public void matchinErrorAnnotationYieldsRestClientUpstreamException() {
        final var client = UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
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
                .build();

        Assertions.assertThrows(RestClientUpstreamException.class, () -> {
            client.callWithJsonPath();
        });

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

        Assertions.assertEquals(true, response.metadata.success);
    }

}
