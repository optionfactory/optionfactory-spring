package net.optionfactory.spring.data.jpa.filtering.filters.text;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "byName", path = "name")
@TextCompare(name = "byDesc", path = "description")
public class EntityForTextCompare {

    @Id
    public long id;
    public String name;
    public String description;

}
