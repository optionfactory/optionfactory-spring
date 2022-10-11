package net.optionfactory.spring.time.jackson.adapters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.MonthDay;

public class MonthDayFromIsoString extends JsonDeserializer<MonthDay> {

    @Override
    public MonthDay deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return MonthDay.parse(p.getText());
    }

}
