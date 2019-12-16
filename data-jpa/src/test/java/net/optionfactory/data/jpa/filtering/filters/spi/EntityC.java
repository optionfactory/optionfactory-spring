package net.optionfactory.data.jpa.filtering.filters.spi;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class EntityC {

    @Id
    public long id;

    @Embedded
    public Inner i;

    @Embeddable
    public static class Inner {

        public long n;
    }
}
