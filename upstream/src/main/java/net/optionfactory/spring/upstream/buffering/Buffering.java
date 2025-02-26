package net.optionfactory.spring.upstream.buffering;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.springframework.http.ResponseEntity;

public enum Buffering {
    BUFFERED, UNBUFFERED, UNBUFFERED_STREAMING;

    public static Buffering responseBufferingFromMethod(Method m) {
        final var rt = m.getGenericReturnType();
        if (rt == InputStream.class) {
            return Buffering.UNBUFFERED_STREAMING;
        }
        if (rt instanceof ParameterizedType pt && pt.getRawType() == ResponseEntity.class && pt.getActualTypeArguments()[0] == InputStream.class) {
            return Buffering.UNBUFFERED_STREAMING;
        }
        return Buffering.BUFFERED;
    }
}
