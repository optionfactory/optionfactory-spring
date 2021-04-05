package net.optionfactory.spring.data.jpa.filtering.chaining.plural;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class Leaf {

    @Id
    public long id;

    @ManyToOne
    public Root root;

    public String color;
}
