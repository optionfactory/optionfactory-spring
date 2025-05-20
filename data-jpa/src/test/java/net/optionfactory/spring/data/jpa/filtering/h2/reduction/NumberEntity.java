package net.optionfactory.spring.data.jpa.filtering.h2.reduction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;

@Entity
@NumberCompare(name = "number", path = "number")
public class NumberEntity {
    @Id
    public long id;
    public long number;
}
