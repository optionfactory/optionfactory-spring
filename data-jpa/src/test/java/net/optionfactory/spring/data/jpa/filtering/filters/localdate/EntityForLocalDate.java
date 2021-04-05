package net.optionfactory.spring.data.jpa.filtering.filters.localdate;

import java.time.LocalDate;
import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;

@Entity
@LocalDateCompare(name = "date", path = "date")
public class EntityForLocalDate {

    @Id
    public long id;
    public LocalDate date;
}
