package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi.paths;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterGroup;

@Entity
@FilterGroup.Subselect(value = "leaves", reuse = false)
@BooleanCompare(name = "flag1", path = "leaves.flag1")
@BooleanCompare(name = "flag2", path = "leaves.flag2")
public class RootEntityWithSubselectFilters {

    @Id
    @GeneratedValue
    public long id;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "rootId")
    public List<LeafEntityWithSubselectFilters> leaves;

    public static RootEntityWithSubselectFilters of(LeafEntityWithSubselectFilters... leaves) {
        final var re = new RootEntityWithSubselectFilters();
        re.leaves = List.of(leaves);
        return re;
    }

}
