package net.optionfactory.spring.data.jpa.filtering.filters.spi.paths;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class LeafEntityWithSubselectFilters {

    @Id
    @GeneratedValue
    public long id;
    public Long rootId;

    public boolean flag1;
    public boolean flag2;

    public static LeafEntityWithSubselectFilters of(boolean flag1, boolean flag2) {
        final var le = new LeafEntityWithSubselectFilters();
        le.flag1 = flag1;
        le.flag2 = flag2;
        return le;
    }

}
