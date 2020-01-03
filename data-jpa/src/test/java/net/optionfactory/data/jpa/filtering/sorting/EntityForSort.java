package net.optionfactory.data.jpa.filtering.sorting;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class EntityForSort {

    @Id
    public long id;

    public long a;

    public String b;
}
