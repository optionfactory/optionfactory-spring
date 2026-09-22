package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository.SessionPolicy;
import org.hibernate.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
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

    @PersistenceContext
    private EntityManager em;

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
        final var all = repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, SessionPolicy.Mode.DEFAULT, (sp, e) -> sp.detaching(e))
                .toList();

        Assertions.assertEquals(1, all.size());
        Assertions.assertTrue(all.stream().noneMatch(em::contains));
    }

    @Test
    public void canStreamAttachedObjects() {
        final var all = repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, SessionPolicy.Mode.DEFAULT, (sp, e) -> e)
                .toList();
        Assertions.assertEquals(1, all.size());
    }

    @Test
    public void defaultModeKeepsEntitiesWritable() {
        final var readOnly = new ArrayList<Boolean>();
        repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, SessionPolicy.Mode.DEFAULT, (sp, e) -> {
            readOnly.add(em.unwrap(Session.class).isReadOnly(e));
            return e;
        }).toList();
        Assertions.assertEquals(List.of(false), readOnly);
    }

    @Test
    public void readOnlyModeSkipsSnapshots() {
        final var readOnly = new ArrayList<Boolean>();
        repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, SessionPolicy.Mode.READ_ONLY, (sp, e) -> {
            readOnly.add(em.unwrap(Session.class).isReadOnly(e));
            return e;
        }).toList();
        Assertions.assertEquals(List.of(true), readOnly);
    }

    @Test
    public void mappingOverloadStreamsReadOnlyAndDetachesAfterMapping() {
        final var seen = new ArrayList<EntityForStream>();
        final var names = repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, (EntityForStream e) -> {
            seen.add(e);
            Assertions.assertTrue(em.unwrap(Session.class).isReadOnly(e));
            return e.name;
        }).toList();
        Assertions.assertEquals(List.of("asd"), names);
        Assertions.assertEquals(1, seen.size());
        Assertions.assertTrue(seen.stream().noneMatch(em::contains));
    }
}
