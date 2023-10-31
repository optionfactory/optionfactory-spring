package net.optionfactory.spring.time.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

public class InstantsTest {

    private final ObjectMapper mapperWithInstantsAsTimestamps;
    private final ObjectMapper mapperWithInstantsAsIsoInstants;
    public InstantsTest() {
        this.mapperWithInstantsAsTimestamps = new ObjectMapper();
        this.mapperWithInstantsAsTimestamps.registerModule(new TimeModule(false));
        this.mapperWithInstantsAsIsoInstants = new ObjectMapper();
        this.mapperWithInstantsAsIsoInstants.registerModule(new TimeModule(true));
    }

    
    @Test
    public void canSerializeInstantAsIsoInstants() throws JsonProcessingException {
        final Instant at = Instant.ofEpochMilli(1234);
        final String got = mapperWithInstantsAsIsoInstants.writeValueAsString(at);
        Assert.assertEquals("1970-01-01T00:00:01.234Z", got);
    }
    
    @Test
    public void canSerializeInstantAsJavaTimestamp() throws JsonProcessingException {
        final Instant at = Instant.ofEpochMilli(1234);
        final String got = mapperWithInstantsAsTimestamps.writeValueAsString(at);
        Assert.assertEquals("1234", got);
    }

    public static class BeanWithInstant {

        public Instant at;
    }

    @Test
    public void canDeserializeInstantFromJavaTimestamp() throws JsonProcessingException {
        final var got = mapperWithInstantsAsTimestamps.readValue("{\"at\": 1234}", BeanWithInstant.class);
        Assert.assertEquals(Instant.ofEpochMilli(1234), got.at);
    }
    
    

    @Test
    public void canDeserializeInstantFromIsoInstant() throws JsonProcessingException {
        final var got = mapperWithInstantsAsIsoInstants.readValue("{\"at\": \"1970-01-01T00:00:01.234Z\"}", BeanWithInstant.class);
        Assert.assertEquals(Instant.ofEpochMilli(1234), got.at);
    }
    
    

    @Test
    public void canSerializeNull() throws JsonProcessingException {
        final BeanWithInstant b = new BeanWithInstant();
        b.at = null;
        final String got = mapperWithInstantsAsTimestamps.writeValueAsString(b);
        Assert.assertEquals("{\"at\":null}", got);
    }
    @Test
    public void canDeserializeNull() throws JsonProcessingException {
        final var got = mapperWithInstantsAsTimestamps.readValue("{\"at\": null}", BeanWithInstant.class);
        Assert.assertEquals(null, got.at);
    }
    
    
}
