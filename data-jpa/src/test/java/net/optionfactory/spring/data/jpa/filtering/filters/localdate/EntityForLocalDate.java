package net.optionfactory.spring.data.jpa.filtering.filters.localdate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDate;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;

@Entity
@LocalDateCompare(name = "date", path = "date")
public class EntityForLocalDate {

    @Id
    public long id;
    public LocalDate date;
}
