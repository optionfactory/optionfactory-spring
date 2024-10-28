package net.optionfactory.spring.data.jpa.filtering.h2.chaining;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Performer {

    @Id
    public long id;
    public String name;
}
