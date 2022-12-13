package net.optionfactory.spring.data.jpa.filtering.filters.text;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "byName", path = "name")
@TextCompare(name = "byDesc", path = "description")
@TextCompare(name = "byTitle", path = "title")
public class EntityForTextCompare {

    @Id
    public long id;
    public String name;
    public String description;
    public String title;

}
