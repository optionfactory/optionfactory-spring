package net.optionfactory.spring.data.jpa.filtering.streaming;

import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

public class StreamTest {

    private EntityForStreamRepository repo;
    private TransactionTemplate tx;

    @Before
    public void setup() {
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(HibernateTestConfig.class);
        ctx.refresh();
        this.repo = ctx.getBean(EntityForStreamRepository.class);
        this.tx = ctx.getBean(TransactionTemplate.class);
        tx.execute(txs -> {
            repo.deleteAll();
            final EntityForStream a = new EntityForStream();
            a.id = 123;
            a.name = "asd";
            repo.save(a);
            return null;
        });
    }

    @Test
    public void canStreamDetachedObjects() {
        final var all = tx.execute(txs
                -> repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, (sp, e) -> e)
                        .collect(Collectors.toList())
        );

        Assert.assertEquals(1, all.size());

    }

    @Test
    public void canStreamAttachedObjects() {
        final var all = tx.execute(txs
                -> repo.findAll(null, FilterRequest.unfiltered(), Sort.unsorted(), 100, (sp, e) -> e)
                        .collect(Collectors.toList())
        );

        Assert.assertEquals(1, all.size());

    }
}
