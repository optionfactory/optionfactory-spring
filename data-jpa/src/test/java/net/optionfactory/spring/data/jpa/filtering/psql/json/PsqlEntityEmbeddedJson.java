package net.optionfactory.spring.data.jpa.filtering.psql.json;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@TextCompare(name = "a", path = "embedded.a")
@TextCompare(name = "b", path = "embedded.b")
@TextCompare(name = "c", path = "embedded.c")
public class PsqlEntityEmbeddedJson {

    @Id
    @GeneratedValue
    public long id;

    /**
     * will be mapped to a jsonb column on postgres, json on mariadb
     */
    @JdbcTypeCode(SqlTypes.JSON)
    public EmbeddedRecord embedded;

    /**
     * Only PSQL, ORACLE and DB2 dialects support querying inside a json column.
     *
     * @Embeddable must be present on the embedded record/class IF fields are
     * used in queries. An UnsupportedException will be thrown "Dialect does not
     * support aggregateComponentAssignmentExpression:
     * org.hibernate.dialect.aggregate.AggregateSupportImpl" when using other
     * dialects.
     * @JdbcTypeCode(SqlTypes.JSON) without @Embeddable can still be used with
     * other dialects.
     */
    @Embeddable
    public record EmbeddedRecord(String a, String b, String c) {

    }
}
