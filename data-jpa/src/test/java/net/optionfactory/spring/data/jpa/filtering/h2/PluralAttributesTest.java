package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class PluralAttributesTest {

    @Entity
    @TextCompare(name = "byLeafColor", path = "leaves.color")
    public static class Root {

        @Id
        public long id;
        @OneToMany(cascade = CascadeType.ALL, mappedBy = "root")
        public List<Leaf> leaves;
    }

    @Entity
    public static class Leaf {

        @Id
        public long id;
        @ManyToOne
        public Root root;
        public String color;
    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
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
        roots.save(r);
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
