package net.optionfactory.data.jpa.filtering;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.filters.FilterWith;

@Entity
@FilterWith(name = "custom", filter = CustomFilter.class)
public class CustomEntity {

    @Id
    public long id;
    public long x;
}
