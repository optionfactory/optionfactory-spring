package net.optionfactory.spring.data.jpa.filtering.h2.chaining.plural;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "byLeafColor", path = "leaves.color")
public class Root {

    @Id
    public long id;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "root")
    public List<Leaf> leaves;
}
