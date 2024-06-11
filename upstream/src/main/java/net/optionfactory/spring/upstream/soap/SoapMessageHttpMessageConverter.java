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

public class SoapMessageHttpMessageConverter implements HttpMessageConverter<SOAPMessage> {

    private final MessageFactory messageFactory;
    private final Protocol protocol;

    public SoapMessageHttpMessageConverter(Protocol protocol) {
        this.protocol = protocol;
        try {
            this.messageFactory = MessageFactory.newInstance(protocol.value);
        } catch (SOAPException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return clazz == SOAPMessage.class;
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return clazz == SOAPMessage.class;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return List.of(protocol.mediaType);
    }

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

    @Override
    public void write(SOAPMessage message, MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        try (var os = outputMessage.getBody()) {
            message.writeTo(os);
        } catch (SOAPException ex) {
            throw new HttpMessageNotWritableException("cannot marshal", ex);
        }
    }

}
