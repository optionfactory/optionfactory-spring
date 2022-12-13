package net.optionfactory.spring.data.jpa.filtering.streaming;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class EntityForStream {

    @Id
    public long id;
    public String name;

}
