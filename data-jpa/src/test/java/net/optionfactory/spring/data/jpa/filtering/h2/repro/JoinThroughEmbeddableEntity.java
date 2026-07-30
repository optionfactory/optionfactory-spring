package net.optionfactory.spring.data.jpa.filtering.h2.repro;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;

@Entity
@NumberCompare(name = "byLeafId", path = "a.b.id")
public class JoinThroughEmbeddableEntity {

    @GeneratedValue
    @Id
    public long id;

    @Embedded
    public MyEmbeddable a;

    @Embeddable
    public static class MyEmbeddable {

        @ManyToOne
        public MyLeaf b;
    }

    @Entity
    public static class MyLeaf {

        @GeneratedValue
        @Id
        public long id;
    }

}
