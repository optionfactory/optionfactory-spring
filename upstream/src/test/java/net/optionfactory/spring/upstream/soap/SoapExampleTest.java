package net.optionfactory.spring.upstream.soap;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.soap.SOAPFault;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.log.UpstreamLogging;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import net.optionfactory.spring.upstream.soap.calculator.Add;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.web.client.HttpServerErrorException.InternalServerError;

@Ignore
public class SoapExampleTest {

    @Test
    public void canDoSoap11Call() throws JAXBException {
        final var client = UpstreamBuilder.create(CalculatorClient.class)
                .soap(Protocol.SOAP_1_1, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .intercept(new UpstreamLogging.Interceptor())
                .build();

        Add req = new Add();
        req.setIntA(3);
        req.setIntB(5);
        final var got = client.add(req);
        Assert.assertEquals(8, got.getAddResult());
    }

    @Test
    public void canDoSoap12Call() throws JAXBException {
        final var client = UpstreamBuilder
                .create(CalculatorClient.class)
                .soap(Protocol.SOAP_1_2, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .intercept(new UpstreamLogging.Interceptor())
                .build();

        Add req = new Add();
        req.setIntA(3);
        req.setIntB(5);
        final var got = client.add(req);
        Assert.assertEquals(8, got.getAddResult());
    }

    @Test
    public void canReadFault() throws JAXBException {
        final var client = UpstreamBuilder
                .create(CalculatorClient.class)
                .soap(Protocol.SOAP_1_1, SoapHeaderWriter.NONE, Add.class)
                .restClient(r -> r.baseUrl("http://www.dneonline.com/calculator.asmx"))
                .intercept(new UpstreamLogging.Interceptor())
                .build();

        Add req = new Add();
        req.setIntA(3);
        req.setIntB(5);
        try {
            client.faultingAdd(req);
            Assert.fail("should not happen");
        } catch (InternalServerError ex) {
            SOAPFault o = ex.getResponseBodyAs(SOAPFault.class);
            Assert.assertEquals("soap:Client", o.getFaultCode());
        }
    }
}
