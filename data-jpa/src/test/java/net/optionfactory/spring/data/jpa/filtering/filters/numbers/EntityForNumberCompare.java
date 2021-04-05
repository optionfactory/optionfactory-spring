package net.optionfactory.spring.data.jpa.filtering.filters.numbers;

import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;

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
