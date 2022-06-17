package net.optionfactory.spring.data.jpa.filtering.slicing;

import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateTestConfig.class)
@Transactional
public class SliceTest {

    @Autowired
    private EntityForSliceRepository repo;

    @Before
    public void setup() {
        repo.deleteAll();
        final EntityForSlice a = new EntityForSlice();
        a.id = 123;
        a.name = "asd";
        repo.save(a);
    }

    @Test
    public void textSlice() {
        Pageable p = PageRequest.of(0, 100);
        Slice<EntityForSlice> findByName = repo.findByName("asd", p);
        Assert.assertFalse(findByName.hasPrevious());
    }
}
