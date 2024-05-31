package net.optionfactory.spring.marshaling.jackson.time.adapters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;

public class InstantFromIsoInstant extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        return Instant.parse(jp.getText());
    }

}
