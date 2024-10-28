package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi.paths;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.util.List;
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
