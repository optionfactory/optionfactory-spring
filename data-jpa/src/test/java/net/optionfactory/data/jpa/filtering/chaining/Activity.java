package net.optionfactory.data.jpa.filtering.chaining;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.chaining.Activity.Season;

@Entity
public class Activity {

    @Id
    public long id;
    public String name;
    @Enumerated(EnumType.STRING)
    public Season season;

    public enum Season {
        SUMMER, WINTER;
    }
}
