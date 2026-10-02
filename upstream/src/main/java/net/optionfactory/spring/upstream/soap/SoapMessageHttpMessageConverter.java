package net.optionfactory.spring.upstream.soap;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import java.io.IOException;
import java.util.List;
import net.optionfactory.spring.upstream.soap.SoapJaxbHttpMessageConverter.Protocol;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

/// Reads and writes raw SAAJ `SOAPMessage`s, for endpoints that work on the whole envelope.
///
/// Installed, with a [SoapJaxbHttpMessageConverter], by the `soap` methods of `UpstreamBuilder`.
public class SoapMessageHttpMessageConverter implements HttpMessageConverter<SOAPMessage> {

    private final MessageFactory messageFactory;
    private final Protocol protocol;

    /// @param protocol the SOAP version of the messages
    /// @throws IllegalStateException when SAAJ does not support the protocol
    public SoapMessageHttpMessageConverter(Protocol protocol) {
        this.protocol = protocol;
        try {
            this.messageFactory = MessageFactory.newInstance(protocol.value);
        } catch (SOAPException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /// @param clazz the target class
    /// @param mediaType the media type, ignored
    /// @return true for `SOAPMessage` only
    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return clazz == SOAPMessage.class;
    }

    /// @param clazz the source class
    /// @param mediaType the media type, ignored
    /// @return true for `SOAPMessage` only
    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return clazz == SOAPMessage.class;
    }

    /// @return the media type of the protocol
    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return List.of(protocol.mediaType);
    }

    /// Creates the message from the response body and headers. SAAJ parses the envelope lazily:
    /// a malformed one may only fail when the message parts are accessed.
    ///
    /// @param clazz the target class
    /// @param inputMessage the response
    /// @return the message
    /// @throws HttpMessageNotReadableException when SAAJ rejects the message
    /// @throws IOException when the response cannot be read
    @Override
    public SOAPMessage read(Class<? extends SOAPMessage> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        final var mh = new MimeHeaders();
        inputMessage.getHeaders().forEach((k, values) -> {
            for (String value : values) {
                mh.addHeader(k, value);
            }
        });
        try (var is = inputMessage.getBody()) {
            return messageFactory.createMessage(mh, is);
        } catch (SOAPException ex) {
            throw new HttpMessageNotReadableException("cannot unmarshal", ex, inputMessage);
        }
    }

    /// @param message the message, written as it is
    /// @param contentType the content type, ignored
    /// @param outputMessage the request
    /// @throws HttpMessageNotWritableException when the message cannot be serialized
    /// @throws IOException when the request cannot be written
    @Override
    public void write(SOAPMessage message, MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        try (var os = outputMessage.getBody()) {
            message.writeTo(os);
        } catch (SOAPException ex) {
            throw new HttpMessageNotWritableException("cannot marshal", ex);
        }
    }

}
