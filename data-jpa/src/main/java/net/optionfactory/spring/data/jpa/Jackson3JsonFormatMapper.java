package net.optionfactory.spring.data.jpa;

import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.json.JsonMapper;

public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {

    public static final String SHORT_NAME = "jackson";
    private final JsonMapper mapper;

    public Jackson3JsonFormatMapper(JsonMapper mapper) {
        this.mapper = mapper;
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
