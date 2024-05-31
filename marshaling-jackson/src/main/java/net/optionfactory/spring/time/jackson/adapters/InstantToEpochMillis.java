package net.optionfactory.spring.time.jackson.adapters;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.Instant;

public class InstantToEpochMillis extends JsonSerializer<Instant> {
    
    @Override
    public void serialize(Instant t, JsonGenerator jg, SerializerProvider sp) throws IOException {
        jg.writeNumber(t.toEpochMilli());
    }
    
}
