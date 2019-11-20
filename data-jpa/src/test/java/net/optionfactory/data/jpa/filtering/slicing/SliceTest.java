package net.optionfactory.data.jpa.filtering.slicing;

import net.optionfactory.data.jpa.HibernateTestConfig;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.support.TransactionTemplate;

public class SliceTest {

    private EntityForSliceRepository repo;
    private TransactionTemplate tx;

    @Before
    public void setup() {
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(HibernateTestConfig.class);
        ctx.refresh();
        this.repo = ctx.getBean(EntityForSliceRepository.class);
        this.tx = ctx.getBean(TransactionTemplate.class);
        tx.execute(txs -> {
            repo.deleteAll();
            final EntityForSlice a = new EntityForSlice();
            a.id = 123;
            a.name = "asd";
            repo.save(a);
            return null;
        });
    }

    @Test
    public void textSlice() {
        
        Pageable p = PageRequest.of(0, 100);
        Slice<EntityForSlice> findByName = repo.findByName("asd", p);
        findByName.hasPrevious();
    }
}
