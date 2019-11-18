package net.optionfactory.data.jpa.filtering.filters;

import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.ActivitiesRepository;
import net.optionfactory.data.jpa.filtering.Activity;
import net.optionfactory.data.jpa.filtering.FilterRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

public class TextCompareTest {

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
    public void textCompareEquals() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.EQUALS.toString(),
            TextCompare.Mode.CASE_SENSITIVE.toString(),
            "asd"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEqualsIgnoreCase() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.EQUALS.toString(),
            TextCompare.Mode.IGNORE_CASE.toString(),
            "ASD"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareContains() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.CONTAINS.toString(),
            TextCompare.Mode.CASE_SENSITIVE.toString(),
            "s"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareContainsIgnoreCase() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.CONTAINS.toString(),
            TextCompare.Mode.IGNORE_CASE.toString(),
            "S"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareStartsWith() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.STARTS_WITH.toString(),
            TextCompare.Mode.CASE_SENSITIVE.toString(),
            "a"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareStartsWithIgnoreCase() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.STARTS_WITH.toString(),
            TextCompare.Mode.IGNORE_CASE.toString(),
            "A"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEndsWith() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.ENDS_WITH.toString(),
            TextCompare.Mode.CASE_SENSITIVE.toString(),
            "d"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEndsWithIgnoreCase() {
        final FilterRequest fr = new FilterRequest();
        fr.put("byName", new String[]{
            TextCompare.Operator.ENDS_WITH.toString(),
            TextCompare.Mode.IGNORE_CASE.toString(),
            "D"
        });
        final Pageable pr = Pageable.unpaged();
        Page<Activity> page = tx.execute(txs -> activities.findAll(fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }
}
