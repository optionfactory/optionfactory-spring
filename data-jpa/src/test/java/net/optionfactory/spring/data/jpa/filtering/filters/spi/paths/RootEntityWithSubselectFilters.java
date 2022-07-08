package net.optionfactory.spring.data.jpa.filtering.filters.spi.paths;

import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.QueryMode;

@Entity
@BooleanCompare(name = "flag1", path = "leaves.flag1", mode = QueryMode.SUBSELECT)
@BooleanCompare(name = "flag2", path = "leaves.flag2", mode = QueryMode.SUBSELECT)
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
