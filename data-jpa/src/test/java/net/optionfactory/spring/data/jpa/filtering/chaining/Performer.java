package net.optionfactory.spring.data.jpa.filtering.chaining;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Performer {

    @Id
    public long id;
    public String name;
}
