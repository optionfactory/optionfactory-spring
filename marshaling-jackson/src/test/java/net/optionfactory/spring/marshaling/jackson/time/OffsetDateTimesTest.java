package net.optionfactory.spring.marshaling.jackson.time;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Assert;
import org.junit.Test;

public class OffsetDateTimesTest {

    private final ObjectMapper mapper;

    public OffsetDateTimesTest() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new TimeModule(false));
    }

    @Test
    public void canSerializeOffsetDateTimeAsIsoOffsetDateTime() throws JsonProcessingException {
        final OffsetDateTime at = OffsetDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneOffset.ofHours(1));
        final String got = mapper.writeValueAsString(at);
        Assert.assertEquals("\"2003-02-01T04:05:06+01:00\"", got);
    }

    public static class BeanWithOffsetDateTime {

        public OffsetDateTime at;
    }

    @Test
    public void canDeserializeOffsetDateTimeFromIsoOffsetDateTime() throws JsonProcessingException {
        final var got = mapper.readValue("{\"at\": \"2003-02-01T04:05:06+01:00\"}", BeanWithOffsetDateTime.class);
        Assert.assertEquals(OffsetDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneOffset.ofHours(1)), got.at);
    }

}
