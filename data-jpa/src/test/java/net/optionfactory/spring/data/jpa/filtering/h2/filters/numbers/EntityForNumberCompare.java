package net.optionfactory.spring.data.jpa.filtering.h2.filters.numbers;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;


@Entity
@NumberCompare(name = "maxPersons", path = "maxPersons")
@NumberCompare(name = "rating", path = "rating")
@NumberCompare(name = "container.value", path = "container.value")
public class EntityForNumberCompare {

    @Id
    public long id;
    public Integer maxPersons;
    public double rating;

    @Embedded
    public NumericEmbeddedContainer container;

    @Embeddable
    public static class NumericEmbeddedContainer {
        public Integer value;
    }
}
