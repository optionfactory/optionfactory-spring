package net.optionfactory.spring.data.jpa.filtering.slicing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class EntityForSlice {

    @Id
    public long id;
    public String name;

}
