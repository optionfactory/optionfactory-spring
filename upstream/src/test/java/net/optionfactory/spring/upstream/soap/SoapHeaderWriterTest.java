package net.optionfactory.spring.upstream.soap;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.rendering.BodyRendering;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.FastByteArrayOutputStream;

public class SoapHeaderWriterTest {

    @Test
    public void canSerializeSoapHeaderUsingWssUsernameToken() throws JAXBException, SOAPException, IOException {
        final var baos = new FastByteArrayOutputStream();
        final var messageFactory = MessageFactory.newInstance(Protocol.SOAP_1_1.value);
        final var hw = new SoapHeaderWriter.WssUsernameToken("username", "password");
        final var soapMessage = messageFactory.createMessage();

        hw.write(soapMessage.getSOAPHeader());
        soapMessage.saveChanges();
        soapMessage.writeTo(baos);

        final var expected = BodyRendering.xsltCompact(BodySource.of("""
                            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                                <SOAP-ENV:Header>
                                    <wsse:Security 
                                        xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" 
                                        xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" 
                                        SOAP-ENV:mustUnderstand="1"
                                    >
                                        <wsse:UsernameToken wsu:Id="UsernameToken-133e1b8e-da33-3c4c-bf7a-508620ca7f10">
                                            <wsse:Username>username</wsse:Username>
                                            <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">password</wsse:Password>
                                        </wsse:UsernameToken>
                                    </wsse:Security>
                                </SOAP-ENV:Header>
                                <SOAP-ENV:Body/>
                            </SOAP-ENV:Envelope>""", StandardCharsets.UTF_8));

        Assert.assertEquals(expected, new String(baos.toByteArrayUnsafe(), StandardCharsets.UTF_8));
    }
}
