package net.optionfactory.spring.data.jpa.filtering.h2.slicing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class EntityForSlice {

    @Id
    public long id;
    public String name;

}
