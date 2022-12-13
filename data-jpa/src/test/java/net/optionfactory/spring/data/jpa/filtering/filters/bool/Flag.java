package net.optionfactory.spring.data.jpa.filtering.filters.bool;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;

@Entity
@BooleanCompare(name = "javaBoolean", path = "data")
@BooleanCompare(name = "yesNoBoolean", path = "data", trueValue = "yes", falseValue = "no")
@BooleanCompare(name = "YNMatchCaseBoolean", path = "data", trueValue = "Y", falseValue = "N")
public class Flag {

    @Id
    public long id;
    public boolean data;
}
