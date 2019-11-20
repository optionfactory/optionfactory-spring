package net.optionfactory.data.jpa.filtering.filters.filterwith;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.filters.Filterable;

@Entity
@Filterable(name = "custom", filter = CustomFilter.class)
public class CustomEntity {

    @Id
    public long id;
    public long x;
}
