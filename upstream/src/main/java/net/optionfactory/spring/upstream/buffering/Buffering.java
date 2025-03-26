package net.optionfactory.spring.upstream.buffering;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;

public enum Buffering {
    BUFFERED, UNBUFFERED, UNBUFFERED_STREAMING;

    public static Buffering responseBufferingFromMethod(Method m) {
        final var rt = m.getGenericReturnType();
        if(isStreamOrInputStream(rt)){
            return Buffering.UNBUFFERED_STREAMING;
        }
        if (rt instanceof ParameterizedType pt && pt.getRawType() == ResponseEntity.class && isStreamOrInputStream(pt.getActualTypeArguments()[0])) {
            return Buffering.UNBUFFERED_STREAMING;
        }
        return Buffering.BUFFERED;
    }
        
    private static boolean isStreamOrInputStream(Type t){
        if (t == InputStream.class) {
            return true;
        }
        return t instanceof ParameterizedType pt && pt.getRawType() == Stream.class;
    }
}
