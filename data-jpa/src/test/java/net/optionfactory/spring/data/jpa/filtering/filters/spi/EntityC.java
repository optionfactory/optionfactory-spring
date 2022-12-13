package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

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
