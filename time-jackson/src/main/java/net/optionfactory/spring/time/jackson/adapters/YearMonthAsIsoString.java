package net.optionfactory.spring.time.jackson.adapters;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.YearMonth;

public class YearMonthAsIsoString extends JsonSerializer<YearMonth> {
    
    @Override
    public void serialize(YearMonth value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.toString());
    }
    
}
