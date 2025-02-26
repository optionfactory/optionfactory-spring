package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

public class InputStreamHttpMessageConverter extends AbstractHttpMessageConverter<InputStream> {

    @Override
    protected boolean supports(Class<?> clazz) {
        return clazz == InputStream.class;
    }

    @Override
    protected InputStream readInternal(Class<? extends InputStream> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return inputMessage.getBody();
    }

    @Override
    protected void writeInternal(InputStream t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        t.transferTo(outputMessage.getBody());
    }

    @Override
    protected boolean canRead(MediaType mediaType) {
        return true;
    }

}
