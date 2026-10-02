package net.optionfactory.spring.data.jpa.web;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.json.JsonMapper;

public class PageMixinTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addMixIn(Page.class, PageMixin.class)
            .build();

    @Test
    public void aPageIsWrittenAsItsTotalAndItsContentOnly() {
        final var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2, Sort.by("name")), 10);
        Assertions.assertEquals(mapper.readTree("{\"size\":10,\"data\":[\"a\",\"b\"]}"), mapper.readTree(mapper.writeValueAsString(page)), "only the total, as size, and the content, as data, are written");
    }

    @Test
    public void anEmptyPageIsWrittenWithAnEmptyArray() {
        Assertions.assertEquals(mapper.readTree("{\"size\":0,\"data\":[]}"), mapper.readTree(mapper.writeValueAsString(Page.empty())), "an empty page has a zero total and no data");
    }
}
