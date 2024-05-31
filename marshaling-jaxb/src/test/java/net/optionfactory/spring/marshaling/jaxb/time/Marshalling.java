package net.optionfactory.spring.marshaling.jaxb.time;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;

public class Marshalling {

    public static <B> String marshal(B bean) throws JAXBException {
        final JAXBContext ctx = JAXBContext.newInstance(bean.getClass());
        final Marshaller marshaller = ctx.createMarshaller();
        final StringWriter writer = new StringWriter();
        marshaller.marshal(bean, writer);
        return writer.toString();
    }

    public static <B> B unmarshal(String source, Class<B> beanType) throws JAXBException {
        final JAXBContext ctx = JAXBContext.newInstance(beanType);
        final Unmarshaller unmarshaller = ctx.createUnmarshaller();
        return (B) unmarshaller.unmarshal(new StringReader(source));        
    }
}
