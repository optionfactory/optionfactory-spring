package net.optionfactory.spring.data.jpa.filtering.psql.json;

import jakarta.inject.Inject;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import net.optionfactory.spring.data.jpa.filtering.psql.HibernateOnPsqlTestConfig;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnPsqlTestConfig.class)
@PerMethodTransactional
public class PsqlEntityEmbeddedJsonTest {

    @Entity
    @TextCompare(name = "a", path = "embedded.a")
    @TextCompare(name = "b", path = "embedded.b")
    @TextCompare(name = "c", path = "embedded.c")
    public static class Root {

        @Id
        @GeneratedValue
        public long id;

        /**
         * will be mapped to a jsonb column on postgres, json on mariadb
         */
        @JdbcTypeCode(SqlTypes.JSON)
        public Embed embedded;

    }

    /**
     * Only PSQL, ORACLE and DB2 dialects support querying inside a json column.
     *
     * @Embeddable must be present on the embedded record/class IF fields are
     * used in queries. An UnsupportedException will be thrown "Dialect does not
     * support aggregateComponentAssignmentExpression:
     * org.hibernate.dialect.aggregate.AggregateSupportImpl" when using other
     * dialects.
     * @JdbcTypeCode(SqlTypes.JSON) without @Embeddable can still be used with
     * other dialects. Using @Embeddable:
     * <ul>
     * <li>the object is serialized as jsonb BUT the configured
     * JacksonJsonFormatMapper will not be used.
     * <li>the deduced JavaType is EmbeddableAggregateJavaType instead of
     * JsonJavaType.
     * <li>the ImplicitNamingStrategy will be used to rename the aggregate
     * components in the serialized jsonb.
     * </ul>
     */
    @Embeddable
    public record Embed(String a, String b, String c) {

    }

    public interface PsqlEntityEmbeddedJsonRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {
    }

    @Inject
    private PsqlEntityEmbeddedJsonRepository entities;

    @Test
    public void canFilterOnAnEmbeddableRecordSerializedAsJsonb() {
        final var e1 = new Root();
        e1.embedded = new Embed("e1a", "e1b", "e1c");
        final var e2 = new Root();
        e1.embedded = new Embed("e2a", "e2b", "e2c");
        entities.save(e1);
        entities.save(e2);

        final var fr = FilterRequest.builder()
                .text("a", f -> f.eq("e2a"))
                .build();

        final var found = entities.findAll(fr);

        Assertions.assertEquals(1, found.size());
    }
}
