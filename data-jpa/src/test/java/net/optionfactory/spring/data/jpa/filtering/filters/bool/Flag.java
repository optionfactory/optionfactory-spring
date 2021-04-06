package net.optionfactory.spring.data.jpa.filtering.filters.bool;

import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;

@Entity
@BooleanCompare(name = "javaBoolean", path = "value")
@BooleanCompare(name = "yesNoBoolean", path = "value", trueValue = "yes", falseValue = "no")
@BooleanCompare(name = "YNMatchCaseBoolean", path = "value", trueValue = "Y", falseValue = "N")
public class Flag {

    @Id
    public long id;
    public boolean value;
}
