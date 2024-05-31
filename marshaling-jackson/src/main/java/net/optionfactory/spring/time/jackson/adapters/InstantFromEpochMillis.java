package net.optionfactory.spring.time.jackson.adapters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;

public class InstantFromEpochMillis extends JsonDeserializer<Instant> {
    
    @Override
    public Instant deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        return Instant.ofEpochMilli(jp.getLongValue());
    }
    
}
