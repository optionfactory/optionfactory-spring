package net.optionfactory.data.jpa.filtering.slicing;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class EntityForSlice {

    @Id
    public long id;
    public String name;

}
