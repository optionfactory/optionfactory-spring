package net.optionfactory.spring.data.jpa.filtering.specification;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "byDesc", property = "description")
public class EntityForSpecification {
    
    @Id
    public long id;
    public String name;
    public String description;
}
