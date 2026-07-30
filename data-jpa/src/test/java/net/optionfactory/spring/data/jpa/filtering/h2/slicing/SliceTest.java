package net.optionfactory.spring.data.jpa.filtering.h2.slicing;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class SliceTest {

    @Entity
    public static class EntityForSlice {

        @Id
        public long id;
        public String name;

    }

    public interface EntityForSliceRepository extends JpaRepository<EntityForSlice, Long>, WhitelistFilteringRepository<EntityForSlice> {

        Slice<EntityForSlice> findByName(String name, Pageable pr);

    }

    @Inject
    private EntityForSliceRepository repo;

    private EntityForSlice entity(long id, String name) {
        final var entity = new EntityForSlice();
        entity.id = id;
        entity.name = name;
        return entity;
    }

    @BeforeEach
    public void setup() {
        repo.deleteAll();
        repo.save(entity(1, "TEST"));
        repo.save(entity(2, "TEST"));
        repo.save(entity(3, "TEST"));
        repo.save(entity(4, "TEST"));
        repo.save(entity(5, "TEST"));
        repo.save(entity(6, "TEST"));
    }

    @Test
    public void firstSliceHasNoPrevious() {
        Pageable p = PageRequest.of(0, 2, Sort.by("id"));
        Slice<EntityForSlice> findByName = repo.findByName("TEST", p);
        Assertions.assertFalse(findByName.hasPrevious());
        Assertions.assertTrue(findByName.hasContent());
    }

    @Test
    public void firstSliceHasNext() {
        Pageable p = PageRequest.of(0, 2, Sort.by("id"));
        Slice<EntityForSlice> findByName = repo.findByName("TEST", p);
        Assertions.assertTrue(findByName.hasNext());
        Assertions.assertTrue(findByName.hasContent());
    }

    @Test
    public void secondSliceHasPrevious() {
        Pageable p = PageRequest.of(1, 2, Sort.by("id"));
        Slice<EntityForSlice> findByName = repo.findByName("TEST", p);
        Assertions.assertTrue(findByName.hasPrevious());
        Assertions.assertTrue(findByName.hasContent());
    }
}
