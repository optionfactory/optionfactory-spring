package net.optionfactory.spring.data.jpa.filtering.h2.chaining.plural;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Leaf {

    @Id
    public long id;

    @ManyToOne
    public Root root;

    public String color;
}
