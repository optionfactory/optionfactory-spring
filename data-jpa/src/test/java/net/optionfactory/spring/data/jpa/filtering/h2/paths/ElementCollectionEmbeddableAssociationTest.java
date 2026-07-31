package net.optionfactory.spring.data.jpa.filtering.h2.paths;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class ElementCollectionEmbeddableAssociationTest {

    @Entity
    @TextCompare(name = "byCountryName", path = "addresses.country.name")
    public static class Person {

        @Id
        public long id;

        @ElementCollection
        public List<Address> addresses;

    }

    @Embeddable
    public static class Address {

        public String street;

        @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
        public Country country;
    }

    @Entity
    public static class Country {

        @Id
        public long id;
        public String name;
    }

    public interface ElementCollectionEmbeddableAssociationRepository extends JpaRepository<Person, Long>, WhitelistFilteringRepository<Person> {
    }

    @Inject
    private ElementCollectionEmbeddableAssociationRepository repo;

    @BeforeEach
    public void setup() {
        repo.deleteAll();

        final var country = new Country();
        country.id = 1;
        country.name = "Italy";

        final var address = new Address();
        address.street = "Via Roma";
        address.country = country;

        final var user = new Person();
        user.id = 10;
        user.addresses = List.of(address);

        repo.save(user);
    }

    @Test
    public void canFilterThroughElementCollectionOfEmbeddablesWithAssociation() {
        final var fr = FilterRequest.builder()
                .text("byCountryName", f -> f.eq("Italy"))
                .build();
        final var page = repo.findAll(fr, Pageable.unpaged());
        Assert.assertEquals(1, page.getTotalElements());
    }

}
