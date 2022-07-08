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
@BooleanCompare(name = "flag1", path = "leaves.flag1", mode = QueryMode.JOIN)
@BooleanCompare(name = "flag2", path = "leaves.flag2", mode = QueryMode.JOIN)
public class RootEntityWithJoinFilters {

    @Id
    @GeneratedValue
    public long id;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "rootId")
    public List<LeafEntityWithJoinFilters> leaves;

    public static RootEntityWithJoinFilters of(LeafEntityWithJoinFilters... leaves) {
        final var re = new RootEntityWithJoinFilters();
        re.leaves = List.of(leaves);
        return re;
    }

}
