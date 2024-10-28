package net.optionfactory.spring.data.jpa.filtering.h2.sorting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
