package net.optionfactory.spring.data.jpa.filtering.h2.specification;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "byDesc", path = "description")
public class EntityForSpecification {
    
    @Id
    public long id;
    public String name;
    public String description;
}
