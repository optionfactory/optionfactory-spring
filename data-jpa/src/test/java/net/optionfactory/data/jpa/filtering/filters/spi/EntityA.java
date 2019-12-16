package net.optionfactory.data.jpa.filtering.filters.spi;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class EntityA {

    @Id
    public long id;

    @ManyToOne
    public EntityB b;
}
