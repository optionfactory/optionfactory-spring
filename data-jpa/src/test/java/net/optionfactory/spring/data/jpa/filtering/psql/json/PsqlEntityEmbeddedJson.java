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

    @JdbcTypeCode(SqlTypes.JSON)
    public EmbeddedRecord embedded;

    @Embeddable
    public record EmbeddedRecord(String a, String b, String c) {

    }
}
