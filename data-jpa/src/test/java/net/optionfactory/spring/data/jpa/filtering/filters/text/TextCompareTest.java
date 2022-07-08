package net.optionfactory.spring.data.jpa.filtering.filters.text;

import java.util.Map;

import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.Operator;
import net.optionfactory.spring.data.jpa.filtering.filters.numbers.EntityForNumberCompare;
import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

public class TextCompareTest {

    private EntityForTextCompareRepository repo;
    private TransactionTemplate tx;

    @Before
    public void setup() {
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(HibernateTestConfig.class);
        ctx.refresh();
        this.repo = ctx.getBean(EntityForTextCompareRepository.class);
        this.tx = ctx.getBean(TransactionTemplate.class);
        tx.execute(txs -> {
            repo.deleteAll();
            final EntityForTextCompare a = new EntityForTextCompare();
            a.id = 123;
            a.name = "asd";
            a.description = "test";
            a.title = null;
            repo.save(a);

            return null;
        });
    }

    @Test
    public void textCompareEquals() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.EQ.toString(),
            TextCompare.CaseSensitivity.CASE_SENSITIVE.toString(),
            "asd"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEqualsIgnoreCase() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.EQ.toString(),
            TextCompare.CaseSensitivity.IGNORE_CASE.toString(),
            "ASD"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareContains() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.CONTAINS.toString(),
            TextCompare.CaseSensitivity.CASE_SENSITIVE.toString(),
            "s"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareContainsIgnoreCase() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.CONTAINS.toString(),
            TextCompare.CaseSensitivity.IGNORE_CASE.toString(),
            "S"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareStartsWith() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.STARTS_WITH.toString(),
            TextCompare.CaseSensitivity.CASE_SENSITIVE.toString(),
            "a"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareStartsWithIgnoreCase() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.STARTS_WITH.toString(),
            TextCompare.CaseSensitivity.IGNORE_CASE.toString(),
            "A"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEndsWith() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.ENDS_WITH.toString(),
            TextCompare.CaseSensitivity.CASE_SENSITIVE.toString(),
            "d"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEndsWithIgnoreCase() {
        final FilterRequest fr = FilterRequest.of(Map.of("byName", new String[]{
            TextCompare.Operator.ENDS_WITH.toString(),
            TextCompare.CaseSensitivity.IGNORE_CASE.toString(),
            "D"
        }));
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }


    @Test
    public void filteringWithNeqIncludesNullValues() {
        final FilterRequest fr = FilterRequest.of(Map.of("byTitle", new String[]{
                Operator.NEQ.toString(),
                TextCompare.CaseSensitivity.IGNORE_CASE.toString(),
                "D"
        }));
        final Page<EntityForTextCompare> all = repo.findAll(Pageable.unpaged());
        final Page<EntityForTextCompare> page = repo.findAll(null, fr, Pageable.unpaged());
        Assert.assertEquals(all.getTotalElements(), page.getTotalElements());
    }
}
