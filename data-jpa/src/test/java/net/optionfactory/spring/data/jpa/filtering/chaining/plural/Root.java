package net.optionfactory.spring.data.jpa.filtering.chaining.plural;

import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "byLeafColor", property = "<JOIN_INNER>leaves.color")
public class Root {

    @Id
    public long id;

    @OneToMany(cascade = CascadeType.ALL)
    public List<Leaf> leaves;
}
