package net.optionfactory.spring.data.jpa.filtering.chaining.plural;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Leaf {

    @Id
    public long id;

    public String color;
}
