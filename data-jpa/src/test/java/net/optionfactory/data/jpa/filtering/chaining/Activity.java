/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.optionfactory.data.jpa.filtering.chaining;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import net.optionfactory.data.jpa.filtering.chaining.Activity.Season;
import net.optionfactory.data.jpa.filtering.filters.InEnum;

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
