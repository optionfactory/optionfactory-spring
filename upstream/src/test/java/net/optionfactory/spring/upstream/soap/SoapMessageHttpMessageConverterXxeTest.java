package net.optionfactory.spring.upstream.soap;

import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.mock.http.MockHttpInputMessage;

public class SoapMessageHttpMessageConverterXxeTest {

    @Test
    public void readIsProtectedAgainstXxeInJDK() throws IOException {
        final var xxePayload = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE foo [
          <!ELEMENT foo ANY >
          <!ENTITY xxe SYSTEM "file:///etc/passwd" >
        ]>
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
           <soapenv:Header/>
           <soapenv:Body>
              <foo>&xxe;</foo>
           </soapenv:Body>
        </soapenv:Envelope>
        """;
        final var inputMessage = new MockHttpInputMessage(xxePayload.getBytes(StandardCharsets.UTF_8));
        inputMessage.getHeaders().add("Content-Type", Protocol.SOAP_1_1.mediaType.toString());
        final var converter = new SoapMessageHttpMessageConverter(Protocol.SOAP_1_1);
        final var message = converter.read(SOAPMessage.class, inputMessage);
        Assertions.assertThrows(SOAPException.class, () -> message.getSOAPBody(), "Expected SAAJ to reject the external entity lookup when reading body");
    }
}
