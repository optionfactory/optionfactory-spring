package net.optionfactory.spring.data.jpa.filtering.h2.repro;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;

@Entity
@NumberCompare(name = "byLeafId", path = "a.b.id")
public class JoinThroughEmbeddableEntity {

    @Id
    public long id;

    @Embedded
    public MyEmbeddable a;

    @Embeddable
    public static class MyEmbeddable {

        @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
        public MyLeaf b;
    }

    @Entity
    public static class MyLeaf {

        @Id
        public long id;
    }

}
