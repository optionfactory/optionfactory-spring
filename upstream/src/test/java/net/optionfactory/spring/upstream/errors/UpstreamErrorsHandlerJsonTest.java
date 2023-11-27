package net.optionfactory.spring.upstream.errors;

import java.net.URI;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import net.optionfactory.spring.upstream.soap.Schemas;
import net.optionfactory.spring.upstream.soap.SoapHeaderWriter;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter;
import net.optionfactory.spring.upstream.soap.calc.AddResponse;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public class UpstreamErrorsHandlerJsonTest {

    @Test(expected = RestClientUpstreamException.class)
    public void matchinErrorAnnotationYieldsRestClientUpstreamException() {
        UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
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
                .interceptor(new UpstreamLoggingInterceptor())
                .responseErrorHandlerFromAnnotations()
                .build()
                .callWithJsonPath();
    }

    @Test
    public void nonMatchinErrorAnnotationYieldsResult() {
        final var response = UpstreamBuilder.create(UpstreamErrorsJsonClient.class)
                .requestFactory((UpstreamHttpInterceptor.InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
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
                .interceptor(new UpstreamLoggingInterceptor())
                .responseErrorHandlerFromAnnotations()
                .build()
                .callWithJsonPath();

        Assert.assertEquals(true, response.metadata.success);
    }

}
