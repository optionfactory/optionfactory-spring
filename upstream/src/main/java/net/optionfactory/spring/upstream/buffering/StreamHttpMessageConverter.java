package net.optionfactory.spring.upstream.buffering;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/// Reads a body as a lazy `Stream<T>`, mapping one element at a time with a Jackson mapper: the
/// elements of a top level array, or a sequence of root values such as JSON lines.
///
/// Any `Stream<T>` target is claimed, without checking that the mapper can deserialize `T`. The
/// stream must be closed, which closes the body and, on a streamed endpoint (see [Buffering]),
/// releases the connection. Errors in the first tokens fail the read, as with any converter, while a
/// malformed element further on is thrown by the stream, as an unchecked Jackson exception, while it
/// is consumed. The converter never writes.
public class StreamHttpMessageConverter extends AbstractGenericHttpMessageConverter<Stream<?>> {

    private final ObjectMapper mapper;

    /// @param mapper maps each element
    /// @param mediaTypes the media types read
    public StreamHttpMessageConverter(ObjectMapper mapper, MediaType... mediaTypes) {
        super(mediaTypes);
        this.mapper = mapper;
    }

    /// @param mapper maps each element
    /// @return a converter reading `text/xml`, `application/xml` and `application/*+xml`
    public static StreamHttpMessageConverter forXml(XmlMapper mapper) {
        return new StreamHttpMessageConverter(mapper, new MediaType("text", "xml", StandardCharsets.UTF_8), new MediaType("application", "xml", StandardCharsets.UTF_8), new MediaType("application", "*+xml", StandardCharsets.UTF_8));
    }

    /// @param mapper maps each element
    /// @return a converter reading `application/jsonl`, `application/json` and `application/*+json`
    public static StreamHttpMessageConverter forJson(JsonMapper mapper) {
        return new StreamHttpMessageConverter(mapper, new MediaType("application", "jsonl"), MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
    }

    @Override
    protected boolean canWrite(MediaType mediaType) {
        return false;
    }

    @Override
    protected void writeInternal(Stream<?> t, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        throw new UnsupportedOperationException("StreamHttpMessageConverter only supports reading");
    }

    /// @param clazz the target class, never a parameterized `Stream` hence never readable
    /// @param mediaType the media type of the body
    /// @return false, as a raw class carries no element type
    @Override
    public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
        return canRead(clazz, null, mediaType);
    }

    /// @param type the target type
    /// @param contextClass the class resolving the type variables of `type`
    /// @param mediaType the media type of the body
    /// @return true for a `Stream<T>` target and a supported media type
    @Override
    public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
        if (!canRead(mediaType)) {
            return false;
        }
        final var t = streamedType(type, contextClass);
        if (t == null) {
            return false;
        }
        return true;
    }

    private JavaType streamedType(Type type, Class<?> contextClass) {
        final var streamType = GenericTypeResolver.resolveType(type, contextClass);
        if (streamType instanceof ParameterizedType pt && pt.getRawType() == Stream.class) {
            final var streamedType = pt.getActualTypeArguments()[0];
            return this.mapper.constructType(streamedType);
        }
        return null;
    }

    @Override
    protected Stream<?> readInternal(Class<? extends Stream<?>> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return readAsStream(streamedType(clazz, null), inputMessage);
    }

    /// @param type the `Stream<T>` target type
    /// @param contextClass the class resolving the type variables of `type`
    /// @param inputMessage the body
    /// @return a sequential stream of the mapped elements, to be closed
    /// @throws IOException when the body cannot be read
    /// @throws HttpMessageNotReadableException when the first tokens cannot be parsed
    @Override
    public Stream<?> read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return readAsStream(streamedType(type, contextClass), inputMessage);
    }

    private Stream<?> readAsStream(JavaType javaType, HttpInputMessage inputMessage) throws IOException {
        try {
            final var is = inputMessage.getBody();
            final var iter = mapper.readerFor(javaType).readValues(is);
            final var spliter = Spliterators.spliteratorUnknownSize(iter, 0);
            return StreamSupport.stream(spliter, false).onClose(() -> {
                    iter.close();
            });
        } catch (InvalidDefinitionException ex) {
            throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
        } catch (JacksonException ex) {
            throw new HttpMessageNotReadableException("JSON parse error: " + ex.getOriginalMessage(), ex, inputMessage);
        }
    }

}
