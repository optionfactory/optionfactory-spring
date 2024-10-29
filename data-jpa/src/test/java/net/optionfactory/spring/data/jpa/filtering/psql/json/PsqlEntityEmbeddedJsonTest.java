package net.optionfactory.spring.data.jpa.filtering.psql.json;

import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.psql.HibernateOnPsqlTestConfig;
import net.optionfactory.spring.data.jpa.filtering.psql.json.PsqlEntityEmbeddedJson.EmbeddedRecord;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnPsqlTestConfig.class)
public class PsqlEntityEmbeddedJsonTest {

    @Autowired
    private PsqlEntityEmbeddedJsonRepository entities;

    @Test
    @Transactional
    public void canFilterOnAnEmbeddableRecordSerializedAsJsonb() {
        final var e1 = new PsqlEntityEmbeddedJson();
        e1.embedded = new EmbeddedRecord("e1a", "e1b", "e1c");
        final var e2 = new PsqlEntityEmbeddedJson();
        e1.embedded = new EmbeddedRecord("e2a", "e2b", "e2c");
        entities.save(e1);
        entities.save(e2);

        final var fr = FilterRequest.builder()
                .text("a", f -> f.eq("e2a"))
                .build();

        final var found = entities.findAll(fr);

        Assert.assertEquals(1, found.size());
    }
}
