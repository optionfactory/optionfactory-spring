package net.optionfactory.spring.data.jpa.filtering.h2.paths;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class ManualTraversalTest {

    public interface RootEntityWithManualTraversalFiltersRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {
    }

    @Entity
    @FilterTraversal(path = "leaves", reuse = false)
    @BooleanCompare(name = "flag1", path = "leaves.flag1")
    @BooleanCompare(name = "flag2", path = "leaves.flag2")
    public static class Root {

        @Id
        @GeneratedValue
        public long id;

        @OneToMany(cascade = CascadeType.ALL)
        @JoinColumn(name = "rootId")
        public List<Leaf> leaves;

        public static Root of(Leaf... leaves) {
            final var re = new Root();
            re.leaves = List.of(leaves);
            return re;
        }

    }

    @Entity
    public static class Leaf {

        @Id
        @GeneratedValue
        public long id;
        public Long rootId;

        public boolean flag1;
        public boolean flag2;

        public static Leaf of(boolean flag1, boolean flag2) {
            final var le = new Leaf();
            le.flag1 = flag1;
            le.flag2 = flag2;
            return le;
        }

    }

    @Inject
    private RootEntityWithManualTraversalFiltersRepository roots;

    @BeforeEach
    public void setup() {
        roots.save(Root.of(Leaf.of(true, true),
                Leaf.of(true, false),
                Leaf.of(false, true),
                Leaf.of(false, false)
        ));
        roots.save(Root.of(Leaf.of(true, false),
                Leaf.of(false, true)
        ));
        roots.save(Root.of(Leaf.of(true, false),
                Leaf.of(false, false)
        ));
    }

    @Test
    public void filtersOnJoinedEntity() {
        //usually when using multiple filters on a PluralAttribute what you
        //want is any root where the filters apply at least once for any
        // this can be achieved by configuring @FilterTraversal(path = "leaves", reuse = false)
        final var fr = FilterRequest.builder()
                .bool("flag1", f -> f.eq(Boolean.TRUE))
                .bool("flag2", f -> f.eq(Boolean.TRUE))
                .build();

        Assertions.assertEquals(2, roots.findAll(null, fr).size());
    }
}
