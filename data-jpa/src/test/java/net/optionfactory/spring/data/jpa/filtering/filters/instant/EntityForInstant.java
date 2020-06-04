package net.optionfactory.spring.data.jpa.filtering.filters.instant;

import java.time.Instant;
import javax.persistence.Entity;
import javax.persistence.Id;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;

@Entity
@InstantCompare(name = "instantIso", property = "instant")
@InstantCompare(name = "instantUnixS", property = "instant", format = InstantCompare.Format.UNIX_S)
@InstantCompare(name = "instantUnixMS", property = "instant", format = InstantCompare.Format.UNIX_MS)
@InstantCompare(name = "instantUnixNS", property = "instant", format = InstantCompare.Format.UNIX_NS)
public class EntityForInstant {

    @Id
    public long id;
    public Instant instant;
}
