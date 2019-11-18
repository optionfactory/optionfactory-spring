package net.optionfactory.data.jpa.filtering;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.Activity.Season;
import net.optionfactory.data.jpa.filtering.filters.InEnum;
import net.optionfactory.data.jpa.filtering.filters.InList;
import net.optionfactory.data.jpa.filtering.filters.PostgresFullTextSearch;
import net.optionfactory.data.jpa.filtering.filters.TextCompare;

@Entity
@TextCompare(name="byName", property="name")
@TextCompare(name="byDesc", property="description")
@InEnum(name="bySeason", property="season", type=Season.class)
@PostgresFullTextSearch(name="fts", properties = {"name", "description"})
@InList(name = "nameIn", property = "name")
@InList(name = "maxPersonsIn", property = "maxPersons")
@InList(name = "ratingIn", property = "rating")
public class Activity {

    @Id
    public long id;
    public String name;
    public String description;
    @Enumerated(EnumType.STRING)
    public Season season;
    public Integer maxPersons;
    public double rating;
    
    public enum Season {
        SUMMER, WINTER;
    }
}
