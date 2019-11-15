package net.optionfactory.data.jpa.filtering;

import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.filters.TextCompare;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.querydsl.QPageRequest;
import org.springframework.transaction.support.TransactionTemplate;

public class SliceTest {

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
            a.id = 123;
            a.name = "asd";
            a.description = "test";
            activities.save(a);

            return null;
        });
    }

    @Test
    public void textSlice() {
        
        Pageable p = PageRequest.of(0, 100);
        Slice<Activity> findByName = activities.findByName("asd", p);
        findByName.hasPrevious();
    }
}
