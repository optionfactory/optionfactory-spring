package net.optionfactory.spring.data.jpa.filtering.chaining;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name = "performerName", path = "performer.name")
@InEnum(name = "activitySeason", path = "activity.season", type = Activity.Season.class)
@InEnum(name = "status", path = "status", type = Appointment.Status.class)
public class Appointment {

    @Id
    public long id;
    @Enumerated(EnumType.STRING)
    public Status status;
    @ManyToOne
    public Activity activity;
    @ManyToOne
    public Performer performer;

    public static enum Status {
        CONFIRMED, CANCELED, PENDING;
    }
}
