package net.optionfactory.spring.upstream.buffering;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

public enum Buffering {
    BUFFERED, UNBUFFERED;

    public static Buffering fromMethod(Method m) {
        final var rt = m.getGenericReturnType();
        if (rt == InputStreamResource.class) {
            return Buffering.UNBUFFERED;
        }
        if (rt instanceof ParameterizedType pt && pt.getRawType() == ResponseEntity.class && pt.getActualTypeArguments()[0] == InputStreamResource.class) {
            return Buffering.UNBUFFERED;
        }
        return Buffering.BUFFERED;
    }
}
