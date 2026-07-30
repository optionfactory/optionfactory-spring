package net.optionfactory.spring.data.jpa.filtering.h2.streaming;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class StreamTest {

    @Entity
    public static class EntityForStream {

        @Id
        public long id;
        public String name;

    }

    public interface EntityForStreamRepository extends JpaRepository<EntityForStream, Long>, WhitelistFilteringRepository<EntityForStream> {

    }

    @Inject
    private EntityForStreamRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        final EntityForStream a = new EntityForStream();
        a.id = 123;
        a.name = "asd";
        repo.save(a);
    }

    @Test
    public void canStreamDetachedObjects() {
        final var all = repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, (sp, e) -> e)
                .toList();

        Assertions.assertEquals(1, all.size());
    }

    @Test
    public void canStreamAttachedObjects() {
        final var all = repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, (sp, e) -> e)
                .toList();
        Assertions.assertEquals(1, all.size());
    }
}
