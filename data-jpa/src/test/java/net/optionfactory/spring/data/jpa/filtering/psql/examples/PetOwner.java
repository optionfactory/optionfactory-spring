package net.optionfactory.spring.data.jpa.filtering.psql.examples;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.psql.examples.Pet.PetType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@TextCompare(name = "byFirstName", path = "firstName")
@TextCompare(name = "byLastName", path = "lastName")
@TextCompare(name = "byState", path = "address.state")
@TextCompare(name = "byPetName", path = "pets.name")
@InEnum(name = "byPetType", path = "pets.type", type = PetType.class)
@LocalDateCompare(name = "byPetBirthDate", path = "pets.birthDate")
public class PetOwner {

    @Id
    @GeneratedValue
    public long id;

    public String firstName;
    public String lastName;

    @JdbcTypeCode(SqlTypes.JSON)
    public Address address;

    @Embeddable
    public record Address(String state, String locality, String route, String streetNumber) {

    }

    @OneToMany(cascade = CascadeType.ALL)
    public List<Pet> pets;

    public static PetOwner of(String firstName, String lastName, Address address, Pet... pets) {
        final var po = new PetOwner();
        po.firstName = firstName;
        po.lastName = lastName;
        po.address = address;
        po.pets = List.of(pets);
        return po;
    }

}
