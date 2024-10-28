package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class EntityA {

    @Id
    public long id;

    @ManyToOne
    public EntityB b;
}
