package net.optionfactory.spring.data.jpa.filtering.h2.slicing;

import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnH2TestConfig.class)
@Transactional
public class SliceTest {

    @Autowired
    private EntityForSliceRepository repo;

    private EntityForSlice entity(long id, String name) {
        final var entity = new EntityForSlice();
        entity.id = id;
        entity.name = name;
        return entity;
    }

    @Before
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
        Assert.assertFalse(findByName.hasPrevious());
        Assert.assertTrue(findByName.hasContent());
    }

    @Test
    public void firstSliceHasNext() {
        Pageable p = PageRequest.of(0, 2, Sort.by("id"));
        Slice<EntityForSlice> findByName = repo.findByName("TEST", p);
        Assert.assertTrue(findByName.hasNext());
        Assert.assertTrue(findByName.hasContent());
    }

    @Test
    public void secondSliceHasPrevious() {
        Pageable p = PageRequest.of(1, 2, Sort.by("id"));
        Slice<EntityForSlice> findByName = repo.findByName("TEST", p);
        Assert.assertTrue(findByName.hasPrevious());
        Assert.assertTrue(findByName.hasContent());
    }
}
