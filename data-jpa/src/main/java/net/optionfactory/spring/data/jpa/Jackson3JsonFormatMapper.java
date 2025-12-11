package net.optionfactory.spring.data.jpa;

import java.io.IOException;
import java.lang.reflect.Type;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {

    public static final String SHORT_NAME = "jackson";
    private final JsonMapper mapper;

    public Jackson3JsonFormatMapper(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> void writeToTarget(T value, JavaType<T> javaType, Object target, WrapperOptions options) {
        mapper.writerFor(mapper.constructType(javaType.getJavaType()))
                .writeValue((JsonGenerator) target, value);
    }

    @Override
    public <T> T readFromSource(JavaType<T> javaType, Object source, WrapperOptions options) throws IOException {
        return mapper.readValue((JsonParser) source, mapper.constructType(javaType.getJavaType()));
    }

    @Override
    public boolean supportsSourceType(Class<?> sourceType) {
        return JsonParser.class.isAssignableFrom(sourceType);
    }

    @Override
    public boolean supportsTargetType(Class<?> targetType) {
        return JsonGenerator.class.isAssignableFrom(targetType);
    }

    @Override
    public <T> T fromString(CharSequence charSequence, Type type) {
        try {
            return mapper.readValue(charSequence.toString(), mapper.constructType(type));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not deserialize string to java type: " + type, e);
        }
    }

    @Override
    public <T> String toString(T value, Type type) {
        try {
            return mapper.writerFor(mapper.constructType(type)).writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not serialize object of java type: " + type, e);
        }
    }

}
