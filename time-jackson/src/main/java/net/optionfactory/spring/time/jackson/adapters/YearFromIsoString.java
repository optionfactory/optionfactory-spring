package net.optionfactory.spring.time.jackson.adapters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Year;

public class YearFromIsoString extends JsonDeserializer<Year> {

    @Override
    public Year deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return Year.parse(p.getText());
    }

}
