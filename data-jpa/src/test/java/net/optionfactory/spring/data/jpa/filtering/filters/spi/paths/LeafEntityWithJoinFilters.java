package net.optionfactory.spring.data.jpa.filtering.filters.spi.paths;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class LeafEntityWithJoinFilters {

    @Id
    @GeneratedValue
    public long id;
    public Long rootId;

    public boolean flag1;
    public boolean flag2;

    public static LeafEntityWithJoinFilters of(boolean flag1, boolean flag2) {
        final var le = new LeafEntityWithJoinFilters();
        le.flag1 = flag1;
        le.flag2 = flag2;
        return le;
    }

}