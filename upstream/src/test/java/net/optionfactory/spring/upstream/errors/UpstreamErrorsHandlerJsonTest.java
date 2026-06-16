package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.UpstreamBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

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
                .baseUri("http://example.com")
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
                .baseUri("http://example.com")
                .json(JsonMapper.builder().build())                
                .build()
                .callWithJsonPath();

        Assertions.assertEquals(true, response.metadata.success);
    }
    
    @Test
    public void responseWithErrorStatusYieldsRestClientUpstreamException() {
        final var client = UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
                .requestFactoryMock(c -> {
                    c.response(HttpStatus.BAD_REQUEST, MediaType.valueOf("application/failures+json"),
                            """
                            [{
                                "type": "FIELD_ERROR",                           
                                "context": "field",
                                "reason": "must not be null"
                            }]
                            """
                    );
                })
                .baseUri("http://example.com")
                .build();

        Assertions.assertThrows(RestClientUpstreamException.class, () -> {
            client.callWithJsonPath();
        });

    }
    

}
