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
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

/// Writes JAXB request objects as SOAP envelopes, and reads JAXB response objects or SOAP faults out
/// of them.
///
/// Installed, with a [SoapMessageHttpMessageConverter], by the `soap` methods of `UpstreamBuilder`.
/// It reads any class annotated with `@XmlRootElement` and `SOAPFault`, and writes any class
/// annotated with `@XmlRootElement`, whatever the media type. When a schema is given both the
/// written and the read bodies are validated against it.
public class SoapJaxbHttpMessageConverter implements HttpMessageConverter<Object> {

    /// The SOAP version spoken, which determines the envelope namespace, the media type and how
    /// the SOAP action is sent.
    public enum Protocol {
        /// SOAP 1.1: `text/xml`, with the action in the `SOAPAction` header.
        SOAP_1_1(SOAPConstants.SOAP_1_1_PROTOCOL, MediaType.TEXT_XML),
        /// SOAP 1.2: `application/soap+xml`, with the action as the `action` media type parameter.
        SOAP_1_2(SOAPConstants.SOAP_1_2_PROTOCOL, new MediaType("application", "soap+xml"));
        /// The SAAJ protocol name, for `MessageFactory.newInstance`.
        public final String value;
        /// The media type of the messages.
        public final MediaType mediaType;

        private Protocol(String value, MediaType mediaType) {
            this.value = value;
            this.mediaType = mediaType;
        }

        private String quoted(String v) {
            return String.format("\"%s\"", v.replace("\\", "\\\\").replace("\"", "\\\""));
        }

        /// The request headers telling the content type and the SOAP action.
        ///
        /// The action is sent as a quoted string, with backslashes and double quotes escaped.
        ///
        /// @param action the SOAP action, if any
        /// @return a new `HttpHeaders` with the `Content-Type` and, for SOAP 1.1 with an action, the
        /// `SOAPAction`
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

    /// @param protocol the SOAP version spoken
    /// @param context the JAXB context of the request and response classes
    /// @param schema validates the bodies, or `null` for no validation
    /// @param headerWriter writes the SOAP header of each request, or `null` for an empty header
    /// @throws IllegalStateException when SAAJ does not support the protocol
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

    /// @param clazz the target class
    /// @param mediaType the media type, ignored
    /// @return true for `@XmlRootElement` classes and `SOAPFault`
    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return clazz.isAnnotationPresent(XmlRootElement.class) || clazz == SOAPFault.class;
    }

    /// @param clazz the source class
    /// @param mediaType the media type, ignored
    /// @return true for `@XmlRootElement` classes
    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return clazz.isAnnotationPresent(XmlRootElement.class);
    }

    /// @return the media type of the protocol
    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return List.of(protocol.mediaType);
    }

    /// Reads the envelope, and returns its fault when `SOAPFault` is asked for, or else unmarshals
    /// the first element of its body as `clazz`.
    ///
    /// @param clazz the target class
    /// @param inputMessage the response
    /// @return the unmarshalled body element, or the fault (`null` when the body has none)
    /// @throws HttpMessageNotReadableException when the envelope cannot be parsed or the body
    /// element cannot be unmarshalled or does not validate; an empty body fails with a
    /// `NullPointerException` instead
    /// @throws IOException when the response cannot be read
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

    /// Writes an envelope, with an xml declaration, whose header is filled by the header writer and
    /// whose body is the marshalled object.
    ///
    /// @param t the object to marshal
    /// @param contentType the content type, ignored: the protocol's comes from the request
    /// initializer
    /// @param outputMessage the request
    /// @throws HttpMessageNotWritableException when the object cannot be marshalled or does not
    /// validate
    /// @throws IOException when the request cannot be written
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
