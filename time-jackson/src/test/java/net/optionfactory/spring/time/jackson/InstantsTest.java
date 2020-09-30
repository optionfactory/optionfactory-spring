package net.optionfactory.spring.time.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

public class InstantsTest {

    private final ObjectMapper mapper;

    public InstantsTest() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new TimeModule());
    }

    @Test
    public void canSerializeInstantAsJavaTimestamp() throws JsonProcessingException {
        final Instant at = Instant.ofEpochMilli(1234);
        final String got = mapper.writeValueAsString(at);
        Assert.assertEquals("1234", got);
    }

    public static class BeanWithInstant {

        public Instant at;
    }

    @Test
    public void canDeserializeInstantFromJavaTimestamp() throws JsonProcessingException {
        final var got = mapper.readValue("{\"at\": 1234}", BeanWithInstant.class);
        Assert.assertEquals(Instant.ofEpochMilli(1234), got.at);
    }

}
