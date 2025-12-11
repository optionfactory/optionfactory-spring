package net.optionfactory.spring.upstream.paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class XmlPathTest {

    @Test
    public void canCheckIfElWithAttributeExists() throws IOException {
        final var data = """
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:u="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                            <s:Body>
                                <EsitoSegnalazione xmlns="http://tempuri.org/">
                                    <CodicePositivo xmlns:i="http://www.w3.org/2001/XMLSchema-instance" i:nil="true"/>
                                    <CodiceErrore>CAMBER20000</CodiceErrore>
                                </EsitoSegnalazione>
                            </s:Body>
                        </s:Envelope>  
                         """;

        final var path = new XmlPath(new ResponseContext(Instant.now(), HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), HttpHeaders.EMPTY, BodySource.of(data, StandardCharsets.UTF_8), false));

        Assertions.assertTrue(path.xpathBool("//CodicePositivo[@nil='true']"));
    }

    @Test
    public void canCheckIfElWithAttributeIsMissing() throws IOException {
        final var data = """
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:u="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                            <s:Body>
                                <EsitoSegnalazione xmlns="http://tempuri.org/">
                                    <CodicePositivo>CAMBOK20000</CodicePositivo>
                                    <CodiceErrore xmlns:i="http://www.w3.org/2001/XMLSchema-instance" i:nil="true" />
                                </EsitoSegnalazione>
                            </s:Body>
                        </s:Envelope>  
                         """;

        final var path = new XmlPath(new ResponseContext(Instant.now(), HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), HttpHeaders.EMPTY, BodySource.of(data, StandardCharsets.UTF_8), false));

        Assertions.assertFalse(path.xpathBool("//CodicePositivo[@nil='true']"));
    }
}
