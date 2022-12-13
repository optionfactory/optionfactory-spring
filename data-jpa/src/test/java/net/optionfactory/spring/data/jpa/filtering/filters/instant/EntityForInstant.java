package net.optionfactory.spring.data.jpa.filtering.filters.instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;

@Entity
@InstantCompare(name = "instantIso", path = "instant")
@InstantCompare(name = "instantUnixS", path = "instant", format = InstantCompare.Format.UNIX_S)
@InstantCompare(name = "instantUnixMS", path = "instant", format = InstantCompare.Format.UNIX_MS)
@InstantCompare(name = "instantUnixNS", path = "instant", format = InstantCompare.Format.UNIX_NS)
public class EntityForInstant {

    @Id
    public long id;
    public Instant instant;
}
