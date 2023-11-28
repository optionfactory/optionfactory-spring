package net.optionfactory.spring.upstream.soap;

import java.io.IOException;
import java.net.URI;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public class UpstreamSoapActionInterceptorTest {

    @Test
    public void soapActionIsDoubleQuoted() throws IOException {
        final Schema schema = Schemas.load(new ClassPathResource("/calculator/schema.xsd"));

        final var capturedHeaders = new AtomicReference<HttpHeaders>();

        UpstreamBuilder.create(CalculatorClient.class)
                .requestFactory((InvocationContext ctx, URI uri, HttpMethod method, HttpHeaders headers) -> {
                    capturedHeaders.set(headers);
                    return MockClientHttpResponse.okUtf8(MediaType.TEXT_XML, """
                                        <?xml version="1.0" encoding="utf-8"?>
                                        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                                            <soap:Body>
                                                <AddResponse xmlns="http://tempuri.org/">
                                                    <AddResult>8</AddResult>
                                                </AddResponse>
                                            </soap:Body>
                                        </soap:Envelope>
                                        """);
                })
                .soap(Protocol.SOAP_1_1, schema, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .build(e -> {})
                .add(new Add());

        Assert.assertEquals("\"http://tempuri.org/Add\"", capturedHeaders.get().getFirst("SOAPAction"));
    }

}
