package net.optionfactory.spring.time.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Assert;
import org.junit.Test;

public class ZonedDateTimesTest {

    private final ObjectMapper mapper;

    public ZonedDateTimesTest() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new TimeModule(false));
    }

    @Test
    public void canSerializeZonedDateTimeAsIsoZonedDateTime() throws JsonProcessingException {
        final ZonedDateTime at = ZonedDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneId.of("Europe/Rome"));
        final String got = mapper.writeValueAsString(at);
        Assert.assertEquals("\"2003-02-01T04:05:06+01:00[Europe/Rome]\"", got);
    }
    @Test
    public void canSerializeZonedDateTimeAsIsoZonedDateTimeForZulu() throws JsonProcessingException {
        final ZonedDateTime at = ZonedDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneId.of("Z"));
        final String got = mapper.writeValueAsString(at);
        Assert.assertEquals("\"2003-02-01T04:05:06Z\"", got);
    }

    public static class BeanWithZonedDateTime {

        public ZonedDateTime at;
    }

    @Test
    public void canDeserializeZonedDateTimeFromIsoZonedDateTime() throws JsonProcessingException {
        final var got = mapper.readValue("{\"at\": \"2003-02-01T04:05:06+01:00[Europe/Rome]\"}", BeanWithZonedDateTime.class);
        Assert.assertEquals(ZonedDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneId.of("Europe/Rome")), got.at);
    }
    
    @Test
    public void canDeserializeZonedDateTimeFromIsoZonedDateTimeForZuluWithOffset() throws JsonProcessingException {
        final var got = mapper.readValue("{\"at\": \"2003-02-01T04:05:06+00:00[Z]\"}", BeanWithZonedDateTime.class);
        Assert.assertEquals(ZonedDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneId.of("Z")), got.at);
    }
    @Test
    public void canDeserializeZonedDateTimeFromIsoZonedDateTimeForZuluWithoutOffset() throws JsonProcessingException {
        final var got = mapper.readValue("{\"at\": \"2003-02-01T04:05:06Z\"}", BeanWithZonedDateTime.class);
        Assert.assertEquals(ZonedDateTime.of(2003, 2, 1, 4, 5, 6, 0, ZoneId.of("Z")), got.at);
    }

}
