package net.optionfactory.spring.upstream.paths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.mocks.MockClientHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class XmlPathTest {

    private static final String ENVELOPE = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                <s:Body>
                    <Result>ok</Result>
                </s:Body>
            </s:Envelope>
            """;

    private static ResponseContext response(BodySource body) {
        return new ResponseContext(Instant.now(), HttpStatus.OK, HttpStatus.OK.getReasonPhrase(), HttpHeaders.EMPTY, body, false);
    }

    private static XmlPath path(String body) {
        return new XmlPath(response(BodySource.of(body, StandardCharsets.UTF_8)));
    }

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

        Assertions.assertTrue(path.xpathBool("//CodicePositivo[@nil='true']"), "the nil attribute of the element must be matched");
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

        Assertions.assertFalse(path.xpathBool("//CodicePositivo[@nil='true']"), "an element without the nil attribute must not be matched");
    }

    @Test
    public void prefixedElementsAreMatchedByLocalName() throws IOException {
        Assertions.assertTrue(path(ENVELOPE).xpathBool("//*[local-name()='Body']/Result/text() = 'ok'"), "prefixed elements must be reachable through local-name()");
        Assertions.assertFalse(path(ENVELOPE).xpathBool("//s:Body"), "a prefix in the expression cannot be resolved, and must yield false");
    }

    @Test
    public void nonBooleanResultsAreConvertedByXPathRules() throws IOException {
        Assertions.assertTrue(path(ENVELOPE).xpathBool("count(//Result)"), "a non-zero number must be true");
        Assertions.assertFalse(path(ENVELOPE).xpathBool("//Missing"), "an empty node set must be false");
    }

    @Test
    public void aMalformedBodyYieldsFalse() throws IOException {
        Assertions.assertFalse(path("<Result>ok").xpathBool("true()"), "a body that cannot be parsed must yield false whatever the expression");
    }

    @Test
    public void anInvalidExpressionYieldsFalse() throws IOException {
        Assertions.assertFalse(path(ENVELOPE).xpathBool("//["), "an expression that does not compile must yield false");
    }

    @Test
    public void aDoctypeIsRejected() throws IOException {
        final var withDoctype = """
                <?xml version="1.0"?>
                <!DOCTYPE Result [<!ENTITY ok "ok">]>
                <Result>&ok;</Result>
                """;
        Assertions.assertFalse(path(withDoctype).xpathBool("true()"), "a document with a doctype must be rejected, yielding false");
    }

    @Test
    public void anUnbufferedBodyYieldsFalse() throws IOException {
        final var streamed = new MockClientHttpResponse(HttpStatus.OK, "OK", HttpHeaders.EMPTY, new ByteArrayResource(ENVELOPE.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertFalse(new XmlPath(response(BodySource.of(streamed, Buffering.UNBUFFERED))).xpathBool("true()"), "a body that is not buffered must not be consumed, and yield false");
    }

    @Test
    public void theBoundHandleEvaluatesAgainstTheResponse() throws Throwable {
        final var handle = XmlPath.xpathBooleanBoundMethodHandle(response(BodySource.of(ENVELOPE, StandardCharsets.UTF_8)));
        Assertions.assertTrue((boolean) handle.invokeWithArguments("//Result = 'ok'"), "the bound handle must evaluate against the bound response");
    }
}
