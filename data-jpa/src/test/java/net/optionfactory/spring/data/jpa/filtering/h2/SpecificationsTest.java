package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class SpecificationsTest {

    @Entity
    @TextCompare(name = "byDesc", path = "description")
    public static class EntityForSpecification {

        @Id
        public long id;
        public String name;
        public String description;
    }

    public interface EntityForSpecificationRepository extends JpaRepository<EntityForSpecification, Long>, WhitelistFilteringRepository<EntityForSpecification> {

        default List<EntityForSpecification> findAllByName(String name, FilterRequest fr) {
            return findAll((root, query, cb) -> cb.equal(root.get("name"), name), fr);
        }
    }

    @Inject
    private EntityForSpecificationRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        final EntityForSpecification a = new EntityForSpecification();
        a.id = 1;
        a.name = "name1";
        a.description = "description";
        repo.save(a);
        final EntityForSpecification b = new EntityForSpecification();
        b.id = 2;
        b.name = "name2";
        b.description = "description";
        repo.save(b);
    }

    @Test
    public void canMixBaseSpecsWithFilterRequest() {
        final var fr = FilterRequest.builder()
                .text("byDesc", f -> f.eq("description"))
                .build();
        List<EntityForSpecification> page = repo.findAllByName("name2", fr);
        Assertions.assertEquals(1, page.size());
    }
}
