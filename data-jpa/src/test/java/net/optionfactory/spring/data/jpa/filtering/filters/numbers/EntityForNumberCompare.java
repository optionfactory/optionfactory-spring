package net.optionfactory.spring.data.jpa.filtering.filters.numbers;

import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@NumberCompare(name = "maxPersons", property = "maxPersons")
@NumberCompare(name = "rating", property = "rating")
@NumberCompare(name = "container.value", property = "container.value")
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
