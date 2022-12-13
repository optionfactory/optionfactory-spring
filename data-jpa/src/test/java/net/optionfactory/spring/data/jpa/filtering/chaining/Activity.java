package net.optionfactory.spring.data.jpa.filtering.chaining;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.chaining.Activity.Season;

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
