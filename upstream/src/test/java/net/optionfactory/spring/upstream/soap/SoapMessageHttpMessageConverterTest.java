package net.optionfactory.spring.upstream.soap;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.namespace.QName;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

public class SoapMessageHttpMessageConverterTest {

    @Test
    public void handlesSoapMessagesOnly() {
        final var converter = new SoapMessageHttpMessageConverter(Protocol.SOAP_1_2);
        Assertions.assertTrue(converter.canRead(SOAPMessage.class, null), "a SOAPMessage must be readable");
        Assertions.assertTrue(converter.canWrite(SOAPMessage.class, null), "a SOAPMessage must be writable");
        Assertions.assertFalse(converter.canRead(String.class, Protocol.SOAP_1_2.mediaType), "anything else must not be readable");
        Assertions.assertEquals(List.of(Protocol.SOAP_1_2.mediaType), converter.getSupportedMediaTypes(), "the supported media type must be the protocol one");
    }

    @Test
    public void aWrittenMessageCanBeReadBack() throws SOAPException, IOException {
        final var message = MessageFactory.newInstance(Protocol.SOAP_1_1.value).createMessage();
        message.getSOAPBody().addChildElement(new QName("urn:test", "Ping", "t")).addTextNode("pong");
        message.saveChanges();
        final var converter = new SoapMessageHttpMessageConverter(Protocol.SOAP_1_1);
        final var out = new MockHttpOutputMessage();
        converter.write(message, Protocol.SOAP_1_1.mediaType, out);

        final var in = new MockHttpInputMessage(out.getBodyAsBytes());
        in.getHeaders().setContentType(Protocol.SOAP_1_1.mediaType);
        final var read = converter.read(SOAPMessage.class, in);
        Assertions.assertEquals("pong", read.getSOAPBody().getFirstChild().getTextContent(), "the body written must be read back");
    }

    @Test
    public void aMessageWithAForeignContentTypeIsRejected() {
        final var in = new MockHttpInputMessage("<x/>".getBytes(StandardCharsets.UTF_8));
        in.getHeaders().set("Content-Type", "application/json");
        final var converter = new SoapMessageHttpMessageConverter(Protocol.SOAP_1_1);
        Assertions.assertThrows(HttpMessageNotReadableException.class, () -> converter.read(SOAPMessage.class, in), "the response headers must reach SAAJ, which rejects a content type that is not SOAP");
    }
}
