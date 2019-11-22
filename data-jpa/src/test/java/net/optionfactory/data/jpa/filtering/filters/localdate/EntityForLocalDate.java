package net.optionfactory.data.jpa.filtering.filters.localdate;

import java.time.LocalDate;
import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.filters.LocalDateCompare;

@Entity
@LocalDateCompare(name = "date", property = "date")
public class EntityForLocalDate {

    @Id
    public long id;
    public LocalDate date;
}
