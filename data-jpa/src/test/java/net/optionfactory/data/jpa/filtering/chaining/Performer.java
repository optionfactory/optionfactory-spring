package net.optionfactory.data.jpa.filtering.chaining;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "name", property = "name")
public class Performer {

    @Id
    public long id;
    public String name;
}
