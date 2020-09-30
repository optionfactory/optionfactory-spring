package net.optionfactory.spring.time.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import org.junit.Assert;
import org.junit.Test;

public class LocalDatesTest {

    private final ObjectMapper mapper;

    public LocalDatesTest() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new TimeModule());
    }

    @Test
    public void canSerializeLocalDateAsIsoLocalDate() throws JsonProcessingException {
        final LocalDate at = LocalDate.of(2003, 2, 1);
        final String got = mapper.writeValueAsString(at);
        Assert.assertEquals("\"2003-02-01\"", got);
    }

    public static class BeanWithLocalDate {

        public LocalDate at;
    }

    @Test
    public void canDeserializeLocalDateFromIsoLocalDate() throws JsonProcessingException {
        final var got = mapper.readValue("{\"at\": \"2003-02-01\"}", BeanWithLocalDate.class);
        Assert.assertEquals(LocalDate.of(2003, 2, 1), got.at);
    }

}
