package net.optionfactory.spring.time.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.Assert;
import org.junit.Test;

public class LocalDateTimesTest {

    private final ObjectMapper mapper;

    public LocalDateTimesTest() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new TimeModule());
    }

    @Test
    public void canSerializeLocalDateTimeAsIsoLocalDateTime() throws JsonProcessingException {
        final LocalDateTime at = LocalDateTime.of(2003, 2, 1, 4, 5, 6);
        final String got = mapper.writeValueAsString(at);
        Assert.assertEquals("\"2003-02-01T04:05:06\"", got);
    }

    public static class BeanWithLocalDateTime {

        public LocalDateTime at;
    }

    @Test
    public void canDeserializeLocalDateTimeFromIsoLocalDateTime() throws JsonProcessingException {
        final var got = mapper.readValue("{\"at\": \"2003-02-01T04:05:06\"}", BeanWithLocalDateTime.class);
        Assert.assertEquals(LocalDateTime.of(2003, 2, 1, 4, 5, 6), got.at);
    }

}
