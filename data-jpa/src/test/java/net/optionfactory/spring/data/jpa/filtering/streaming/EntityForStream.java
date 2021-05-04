package net.optionfactory.spring.data.jpa.filtering.streaming;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class EntityForStream {

    @Id
    public long id;
    public String name;

}
