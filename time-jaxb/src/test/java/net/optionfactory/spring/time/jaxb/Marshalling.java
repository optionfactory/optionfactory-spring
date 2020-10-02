package net.optionfactory.spring.time.jaxb;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

public class Marshalling {

    public static <B> String marshall(B bean) throws JAXBException {
        final JAXBContext ctx = JAXBContext.newInstance(bean.getClass());
        final Marshaller marshaller = ctx.createMarshaller();
        final StringWriter writer = new StringWriter();
        marshaller.marshal(bean, writer);
        return writer.toString();
    }

    public static <B> B unmarshall(String source, Class<B> beanType) throws JAXBException {
        final JAXBContext ctx = JAXBContext.newInstance(beanType);
        final Unmarshaller unmarshaller = ctx.createUnmarshaller();
        return (B) unmarshaller.unmarshal(new StringReader(source));        
    }
}
