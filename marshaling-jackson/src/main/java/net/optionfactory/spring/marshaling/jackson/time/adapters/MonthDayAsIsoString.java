package net.optionfactory.spring.marshaling.jackson.time.adapters;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.MonthDay;

public class MonthDayAsIsoString extends JsonSerializer<MonthDay> {

    @Override
    public void serialize(MonthDay value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.toString());
    }

}
