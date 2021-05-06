package net.optionfactory.spring.data.jpa.filtering.sorting;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable;

@Entity
@Sortable(name = "byA", path = "a")
@Sortable(name = "byB", path = "b")
public class EntityForSort {

    @Id
    public long id;

    public long a;

    public String b;
}
