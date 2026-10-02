package net.optionfactory.spring.upstream.buffering;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;

/// How the response of an endpoint is held: in memory, so that logs, alerts, error conditions and
/// the mapping can all read it, or streamed to the caller.
///
/// The upstream request factories choose it per endpoint with [#responseBufferingFromMethod].
public enum Buffering {
    /// The body is read into memory on first access, and can be read any number of times.
    BUFFERED,
    /// The response of the underlying request factory is used as is.
    UNBUFFERED,
    /// The body is handed to the caller, who must close it to release the connection; logs,
    /// alerts and error conditions cannot inspect it.
    UNBUFFERED_STREAMING;

    /// Streams the endpoints returning an `InputStream`, a `Stream<T>`, or a `ResponseEntity` of either,
    /// and buffers every other one, a raw `Stream` included.
    ///
    /// @param m the endpoint method
    /// @return `UNBUFFERED_STREAMING` for the streaming return types, `BUFFERED` otherwise
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
