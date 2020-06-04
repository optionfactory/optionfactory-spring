package net.optionfactory.spring.data.jpa.filtering.filters.inlist;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.InList;

@Entity
@InList(name = "nameIn", property = "name")
@InList(name = "maxPersonsIn", property = "maxPersons")
@InList(name = "ratingIn", property = "rating")
public class EntityForInList {

    @Id
    public long id;
    public String name;
    public Integer maxPersons;
    public double rating;

}
