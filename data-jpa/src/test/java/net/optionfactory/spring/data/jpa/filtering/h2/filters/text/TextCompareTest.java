package net.optionfactory.spring.data.jpa.filtering.h2.filters.text;

import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.Operator;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
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
        ctx.register(HibernateOnH2TestConfig.class);
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
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.eq("asd"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEqualsIgnoreCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.eq(CaseSensitivity.IGNORE_CASE, "ASD"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareContains() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains("s"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareContainsIgnoreCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "S"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareStartsWith() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.startsWith("a"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareStartsWithIgnoreCase() {

        final var fr = FilterRequest.builder()
                .text("byName", f -> f.startsWith(CaseSensitivity.IGNORE_CASE, "A"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEndsWith() {

        final var fr = FilterRequest.builder()
                .text("byName", f -> f.endsWith("d"))
                .build();

        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void textCompareEndsWithIgnoreCase() {
        final var fr = FilterRequest.builder()
                .text("byName", f -> f.endsWith(CaseSensitivity.IGNORE_CASE, "D"))
                .build();
        final Pageable pr = Pageable.unpaged();
        Page<EntityForTextCompare> page = tx.execute(txs -> repo.findAll(null, fr, pr));
        Assert.assertEquals(123L, page.getContent().get(0).id);
    }

    @Test
    public void filteringWithNeqIncludesNullValues() {

        final var fr = FilterRequest.builder()
                .text("byTitle", f -> f.neq(CaseSensitivity.IGNORE_CASE, "D"))
                .build();

        final Page<EntityForTextCompare> all = repo.findAll(Pageable.unpaged());
        final Page<EntityForTextCompare> page = repo.findAll(null, fr, Pageable.unpaged());
        Assert.assertEquals(all.getTotalElements(), page.getTotalElements());
    }
}
