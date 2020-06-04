package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class EntityB {

    @Id
    public long id;

    @ManyToOne
    public EntityC c;
}
