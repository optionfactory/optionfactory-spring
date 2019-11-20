package net.optionfactory.data.jpa.filtering.filters.numbers;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.filters.NumberCompare;

@Entity
@NumberCompare(name = "maxPersons", property = "maxPersons")
@NumberCompare(name = "rating", property = "rating")
public class EntityForNumberCompare {

    @Id
    public long id;
    public Integer maxPersons;
    public double rating;

}
