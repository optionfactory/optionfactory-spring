package net.optionfactory.spring.data.jpa.filtering.h2.chaining.plural;

import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@Transactional
public class PluralAttributesChainTest {

    @Autowired
    private RootsRepository roots;

    @BeforeEach
    public void setup() {
        final Root r = new Root();
        r.leaves = List.of(new Leaf(), new Leaf());
        r.leaves.get(0).id = 1;
        r.leaves.get(0).root = r;
        r.leaves.get(0).color = "brown";
        r.leaves.get(1).id = 2;
        r.leaves.get(1).root = r;
        r.leaves.get(1).color = "green";
        final Root root = roots.save(r);
    }

    @Test
    public void setupIsGoodEnough() {
        final var fr = FilterRequest
                .builder()
                .text("byLeafColor", f -> f.eq("brown"))
                .build();
        final Pageable pr = Pageable.unpaged();
        final Page<Root> page = roots.findAll(null, fr, pr);
        Assertions.assertEquals(2, page.getContent().get(0).leaves.size());

    }
}
