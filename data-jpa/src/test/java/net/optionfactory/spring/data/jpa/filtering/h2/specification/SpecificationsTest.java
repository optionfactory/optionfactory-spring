package net.optionfactory.spring.data.jpa.filtering.h2.specification;

import java.util.List;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

public class SpecificationsTest {

    private EntityForSpecificationRepository repo;
    private TransactionTemplate tx;

    @Before
    public void setup() {
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(HibernateOnH2TestConfig.class);
        ctx.refresh();
        this.repo = ctx.getBean(EntityForSpecificationRepository.class);
        this.tx = ctx.getBean(TransactionTemplate.class);
        tx.execute(txs -> {
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

            return null;
        });
    }

    @Test
    public void canMixBaseSpecsWithFilterRequest() {
        final FilterRequest fr = FilterRequest.of(Map.of("byDesc", new String[]{
            TextCompare.Operator.EQ.toString(),
            TextCompare.CaseSensitivity.CASE_SENSITIVE.toString(),
            "description"
        }));
        List<EntityForSpecification> page = tx.execute(txs -> repo.findAllByName("name2", fr));
        Assert.assertEquals(1, page.size());
    }
}
