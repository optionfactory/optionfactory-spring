package net.optionfactory.spring.upstream.soap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.soap.SOAPMessage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.validation.Schema;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

public class SoapJaxbHttpMessageConverter implements HttpMessageConverter<Object> {

    public enum Protocol {
        SOAP_1_1(SOAPConstants.SOAP_1_1_PROTOCOL, MediaType.TEXT_XML),
        SOAP_1_2(SOAPConstants.SOAP_1_2_PROTOCOL, new MediaType("application", "soap+xml"));
        public final String value;
        public final MediaType mediaType;

        private Protocol(String value, MediaType mediaType) {
            this.value = value;
            this.mediaType = mediaType;
        }

        private String quoted(String v) {
            return String.format("\"%s\"", v.replace("\\", "\\\\").replace("\"", "\\\""));
        }

        public HttpHeaders headers(Optional<String> action) {
            final var headers = new HttpHeaders();
            if (this == SOAP_1_1) {
                headers.setContentType(mediaType);
                action.ifPresent(a -> headers.set("SOAPAction", quoted(a)));
                return headers;
            }
            final var params = action.map(a -> Map.of("action", quoted(a))).orElse(Map.of());
            headers.setContentType(new MediaType(mediaType.getType(), mediaType.getSubtype(), params));
            return headers;
        }
    }
    private final Protocol protocol;
    private final JAXBContext context;
    private final Schema schema;
    private final SoapHeaderWriter headerWriter;
    private final MessageFactory messageFactory;

    public SoapJaxbHttpMessageConverter(Protocol protocol, JAXBContext context, @Nullable Schema schema, @Nullable SoapHeaderWriter headerWriter) {
        this.protocol = protocol;
        this.context = context;
        this.schema = schema;
        this.headerWriter = headerWriter;
        try {
            this.messageFactory = MessageFactory.newInstance(protocol.value);
        } catch (SOAPException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return clazz.isAnnotationPresent(XmlRootElement.class) || clazz == SOAPFault.class;
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return clazz.isAnnotationPresent(XmlRootElement.class);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return List.of(protocol.mediaType);
    }

    @Override
    public Object read(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        try (var is = inputMessage.getBody()) {
            final SOAPMessage message = messageFactory.createMessage(null, is);
            if (clazz == SOAPFault.class) {
                return message.getSOAPBody().getFault();
            }
            final Unmarshaller unmarshaller = context.createUnmarshaller();
            unmarshaller.setSchema(schema);
            return unmarshaller.unmarshal(firstSoapElement(message.getSOAPBody()), clazz).getValue();
        } catch (JAXBException | SOAPException ex) {
            throw new HttpMessageNotReadableException("cannot unmarshal", ex, inputMessage);
        }
    }

    private static SOAPElement firstSoapElement(SOAPBody body) {
        final var iter = body.getChildElements();
        while (iter.hasNext()) {
            if (iter.next() instanceof SOAPElement se) {
                return se;
            }
        }
        return null;
    }

    @Override
    public void write(Object t, MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        try (var os = outputMessage.getBody()) {
            final SOAPMessage message = messageFactory.createMessage();
            message.setProperty(SOAPMessage.WRITE_XML_DECLARATION, "true");
            if (headerWriter != null) {
                headerWriter.write(message.getSOAPHeader());
            }
            final Marshaller marshaller = context.createMarshaller();
            marshaller.setSchema(schema);
            marshaller.marshal(t, message.getSOAPBody());
            message.saveChanges();
            message.writeTo(os);
        } catch (JAXBException | SOAPException ex) {
            throw new HttpMessageNotWritableException("cannot marshal", ex);
        }
    }

}
