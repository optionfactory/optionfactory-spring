package net.optionfactory.spring.marshaling.jackson.time;

import net.optionfactory.spring.marshaling.jackson.time.TimeModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import org.junit.Assert;
import org.junit.Test;

public class ZonedIdsTest {

    private final ObjectMapper mapper;

    public ZonedIdsTest() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new TimeModule(false));
    }

    @Test
    public void canSerializeZonedIdAsIdString() throws JsonProcessingException {
        final ZoneId id = ZoneId.of("Europe/Rome");
        final String got = mapper.writeValueAsString(id);
        Assert.assertEquals("\"Europe/Rome\"", got);
    }

    @Test
    public void canSerializeZonedIdAsIdStringForZulu() throws JsonProcessingException {
        final ZoneId zulu = ZoneId.of("Z");
        final String got = mapper.writeValueAsString(zulu);
        Assert.assertEquals("\"Z\"", got);
    }

    public static class BeanWithZoneId {

        public ZoneId id;
    }

    @Test
    public void canDeserializeZonedIdFromIdString() throws JsonProcessingException {
        final var got = mapper.readValue("{\"id\": \"Europe/Rome\"}", BeanWithZoneId.class);
        Assert.assertEquals(ZoneId.of("Europe/Rome"), got.id);
    }

    @Test
    public void canDeserializeZonedIdFromIdStringForZulu() throws JsonProcessingException {
        final var got = mapper.readValue("{\"id\": \"Z\"}", BeanWithZoneId.class);
        Assert.assertEquals(ZoneId.of("Z"), got.id);
    }

}
