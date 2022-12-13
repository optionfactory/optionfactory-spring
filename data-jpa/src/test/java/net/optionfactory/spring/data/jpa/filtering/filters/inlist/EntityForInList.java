package net.optionfactory.spring.data.jpa.filtering.filters.inlist;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.InList;

@Entity
@InList(name = "nameIn", path = "name")
@InList(name = "maxPersonsIn", path = "maxPersons")
@InList(name = "ratingIn", path = "rating")
public class EntityForInList {

    @Id
    public long id;
    public String name;
    public Integer maxPersons;
    public double rating;

}
