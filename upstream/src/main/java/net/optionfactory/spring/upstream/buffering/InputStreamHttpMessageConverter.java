package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

/// Reads a body of any media type as an `InputStream`, handing over the body stream itself unread.
///
/// Only the `InputStream` type itself is supported. On a streamed endpoint (see [Buffering]) the
/// caller then owns the connection, and must close the stream. Writing an `InputStream` body copies
/// it, and is only possible when no specific content type is requested, since the converter declares
/// no media type.
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
