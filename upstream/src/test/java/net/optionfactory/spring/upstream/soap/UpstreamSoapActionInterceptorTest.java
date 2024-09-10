package net.optionfactory.spring.upstream.soap;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.validation.Schema;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.calc.Add;
import net.optionfactory.spring.upstream.soap.calc.CalculatorClient;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public class UpstreamSoapActionInterceptorTest {

    @Test
    public void soapActionHeaderIsAddedForSoap11() throws IOException {
        final Schema schema = Schemas.load(new ClassPathResource("/calculator/schema.xsd"));

        final var capturedHeaders = new AtomicReference<HttpHeaders>();

        UpstreamBuilder.create(CalculatorClient.class)
                .requestFactoryMock(c -> {
                    c.responseFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                        capturedHeaders.set(headers);

                        final HttpHeaders h = new HttpHeaders();
                        h.setContentType(MediaType.TEXT_XML);
                        final var content = """
                                            <?xml version="1.0" encoding="utf-8"?>
                                            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                                <soap:Body>
                                                    <AddResponse xmlns="http://tempuri.org/">
                                                        <AddResult>8</AddResult>
                                                    </AddResponse>
                                                </soap:Body>
                                            </soap:Envelope>
                                            """;
                        return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), h, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
                    });
                })
                .soap(Protocol.SOAP_1_1, schema, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .build()
                .add(new Add());

        Assert.assertEquals("\"http://tempuri.org/Add\"", capturedHeaders.get().getFirst("SOAPAction"));
    }

    @Test
    public void soapActionIsAddedAsContentTypeParameterForSoap12() throws IOException {
        final Schema schema = Schemas.load(new ClassPathResource("/calculator/schema.xsd"));

        final var capturedHeaders = new AtomicReference<HttpHeaders>();

        UpstreamBuilder.create(CalculatorClient.class)
                .requestFactoryMock(c -> {
                    c.responseFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                        capturedHeaders.set(headers);

                        final HttpHeaders h = new HttpHeaders();
                        h.setContentType(MediaType.TEXT_XML);
                        final var content = """
                                        <?xml version="1.0" encoding="utf-8"?>
                                        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                            <soap:Body>
                                                <AddResponse xmlns="http://tempuri.org/">
                                                    <AddResult>8</AddResult>
                                                </AddResponse>
                                            </soap:Body>
                                        </soap:Envelope>
                                        """;

                        return new MockClientHttpResponse(HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), h, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));

                    });
                })
                .soap(Protocol.SOAP_1_2, schema, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .build()
                .add(new Add());

        Assert.assertEquals("\"http://tempuri.org/Add\"", capturedHeaders.get().getContentType().getParameter("action"));
    }

}
