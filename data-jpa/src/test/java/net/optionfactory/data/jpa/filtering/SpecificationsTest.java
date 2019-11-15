package net.optionfactory.data.jpa.filtering;

import java.util.List;
import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.filters.TextCompare;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

public class SpecificationsTest {

    private ActivitiesRepository activities;
    private TransactionTemplate tx;

    @Before
    public void setup() {
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(HibernateTestConfig.class);
        ctx.refresh();
        this.activities = ctx.getBean(ActivitiesRepository.class);
        this.tx = ctx.getBean(TransactionTemplate.class);
        tx.execute(txs -> {
            activities.deleteAll();
            final Activity a = new Activity();
            a.id = 1;
            a.name = "name1";
            a.description = "description";
            activities.save(a);
            final Activity b = new Activity();
            b.id = 2;
            b.name = "name2";
            b.description = "description";
            activities.save(b);

            return null;
        });
    }
    @Test
    public void canMixBaseSpecsWithFilterRequest() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byDesc", new String[]{
            TextCompare.Operator.EQUALS.toString(),
            TextCompare.Mode.CASE_SENSITIVE.toString(),
            "description"        
        });
        List<Activity> page = tx.execute(txs -> activities.findAllByName("name2", fr));        
        Assert.assertEquals(1, page.size());
    }
}
