package net.optionfactory.spring.upstream.soap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.calc.Add;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;

public class SoapMarshallingTest {

    @Test
    public void canMarshalAndUnmarshal() throws SOAPException, IOException, JAXBException {
        final var context = JAXBContext.newInstance(Add.class.getPackageName());
        final var req = new Add();
        req.intA = 123;
        req.intB = 345;
        final Protocol protocol = Protocol.SOAP_1_2;
        final SoapHeaderWriter headerWriter = null;
        final var c = new SoapJaxbHttpMessageConverter(protocol, context, headerWriter);
        final var baos = new ByteArrayOutputStream();

        c.write(req, protocol.mediaType, new HttpOutputMessage() {
            @Override
            public OutputStream getBody() throws IOException {
                return baos;
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        });

        final var read = (Add) c.read(Add.class, new HttpInputMessage() {
            @Override
            public InputStream getBody() throws IOException {
                return new ByteArrayInputStream(baos.toByteArray());
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        });
        Assert.assertEquals(123, read.intA);
        Assert.assertEquals(345, read.intB);
    }

}
