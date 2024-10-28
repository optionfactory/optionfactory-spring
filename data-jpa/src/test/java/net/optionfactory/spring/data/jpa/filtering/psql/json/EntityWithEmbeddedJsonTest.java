package net.optionfactory.spring.data.jpa.filtering.psql.json;

import net.optionfactory.spring.data.jpa.filtering.psql.HibernateOnPostgresTestConfig;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.psql.json.EntityWithEmbeddedJson.EmbeddedRecord;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnPostgresTestConfig.class)
@Transactional
public class EntityWithEmbeddedJsonTest {

    @Autowired
    private EntityWithEmbeddedJsonRepository entities;

    @Test
    public void canFilterOnAnEmbeddableRecordSerializedAsJsonb() {
        EntityWithEmbeddedJson e1 = new EntityWithEmbeddedJson();
        e1.embedded = new EmbeddedRecord("e1a", "e1b", "e1c");
        EntityWithEmbeddedJson e2 = new EntityWithEmbeddedJson();
        e1.embedded = new EmbeddedRecord("e2a", "e2b", "e2c");
        entities.save(e1);
        entities.save(e2);

        final var fr = FilterRequest.of(Map.of("a", new String[]{
            TextCompare.Operator.EQ.toString(),
            TextCompare.CaseSensitivity.CASE_SENSITIVE.toString(),
            "e2a"
        }));

        final var found = entities.findAll(fr);

        Assert.assertEquals(1, found.size());
    }
}
