package net.optionfactory.spring.upstream.buffering;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

public class StreamHttpMessageConverter extends AbstractGenericHttpMessageConverter<Stream<?>> {

    private final ObjectMapper mapper;

    public StreamHttpMessageConverter(ObjectMapper mapper, MediaType... mediaTypes) {
        super(mediaTypes);
        this.mapper = mapper;
    }

    public static StreamHttpMessageConverter forXml(ObjectMapper mapper) {
        return new StreamHttpMessageConverter(mapper, new MediaType("text", "xml", StandardCharsets.UTF_8), new MediaType("application", "xml", StandardCharsets.UTF_8), new MediaType("application", "*+xml", StandardCharsets.UTF_8));
    }

    public static StreamHttpMessageConverter forJson(ObjectMapper mapper) {
        return new StreamHttpMessageConverter(mapper, new MediaType("application", "jsonl"), MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
    }

    @Override
    protected boolean canWrite(MediaType mediaType) {
        return false;
    }

    @Override
    protected void writeInternal(Stream<?> t, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
        return canRead(clazz, null, mediaType);
    }

    @SuppressWarnings("deprecation") // as of Jackson 2.18: can(De)Serialize
    @Override
    public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
        if (!canRead(mediaType)) {
            return false;
        }
        final var t = streamedType(type, contextClass);
        if (t == null) {
            return false;
        }
        return mapper.canDeserialize(t);
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
                try {
                    iter.close();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (InvalidDefinitionException ex) {
            throw new HttpMessageConversionException("Type definition error: " + ex.getType(), ex);
        } catch (JsonProcessingException ex) {
            throw new HttpMessageNotReadableException("JSON parse error: " + ex.getOriginalMessage(), ex, inputMessage);
        }
    }

}
