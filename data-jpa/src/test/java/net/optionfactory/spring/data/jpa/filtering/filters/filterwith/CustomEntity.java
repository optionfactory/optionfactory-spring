package net.optionfactory.spring.data.jpa.filtering.filters.filterwith;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable;

@Entity
@Filterable(name = "custom", filter = CustomFilter.class)
public class CustomEntity {

    @Id
    public long id;
    public long x;
}
