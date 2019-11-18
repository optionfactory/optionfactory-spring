package net.optionfactory.data.jpa.filtering;

import java.time.Instant;
import java.time.LocalDate;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import net.optionfactory.data.jpa.filtering.filters.InEnum;
import net.optionfactory.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "performerName", property = "performer.name")
@InEnum(name = "season", property = "activity.season", type = Activity.Season.class)
@InEnum(name = "status", property = "status", type = Appointment.Status.class)
@LocalDateCompare(name = "date", property = "date")
public class Appointment {

    @Id
    public long id;
    public Instant created;
    public LocalDate date;
    @ManyToOne
    public Activity activity;
    @ManyToOne
    public Performer performer;
    @Enumerated(EnumType.STRING)
    public Status status;

    public static enum Status {
        CONFIRMED, CANCELED, PENDING;
    }
}
