package net.optionfactory.spring.data.jpa.filtering.psql.examples;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import net.optionfactory.spring.data.jpa.filtering.psql.HibernateOnPsqlTestConfig;
import net.optionfactory.spring.data.jpa.filtering.psql.examples.PetOwnerExampleTest.Pet.PetType;
import net.optionfactory.spring.data.jpa.filtering.psql.examples.PetOwnerExampleTest.PetOwner.Address;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(HibernateOnPsqlTestConfig.class)
@PerMethodTransactional
public class PetOwnerExampleTest {

    @Entity
    public static class Pet {

        @Id
        @GeneratedValue
        public long id;

        public enum PetType {
            CAT, DOG;
        };

        @Enumerated(EnumType.STRING)
        public PetType type;
        public String breed;
        public String name;
        public LocalDate birthDate;

        public static Pet of(PetType type, String breed, String name, LocalDate birthDate) {
            final var p = new Pet();
            p.type = type;
            p.breed = breed;
            p.name = name;
            p.birthDate = birthDate;
            return p;
        }

    }

    @Entity
    @TextCompare(name = "byFirstName", path = "firstName")
    @TextCompare(name = "byLastName", path = "lastName")
    @TextCompare(name = "byState", path = "address.state")
    @TextCompare(name = "byPetName", path = "pets.name")
    @InEnum(name = "byPetType", path = "pets.type", type = PetType.class)
    @LocalDateCompare(name = "byPetBirthDate", path = "pets.birthDate")
    public static class PetOwner {

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

    public interface PetOwnersRepository extends JpaRepository<PetOwner, Long>, WhitelistFilteringRepository<PetOwner> {

    }

    @Inject
    private PetOwnersRepository owners;

    @BeforeEach
    public void setup() {
        owners.deleteAll();
        owners.save(PetOwner.of("Dorothy", "Gale",
                new Address("Kansas", "Salina", "Somewhere", "13/B"),
                Pet.of(PetType.DOG, "Cairn Terrier", "Toto", LocalDate.of(1800, Month.MARCH, 12))
        ));
        owners.save(PetOwner.of("Hermione", "Granger",
                new Address("England", "Hogwarts", "Castle", "1"),
                Pet.of(PetType.CAT, "Persian", "Crookshanks", LocalDate.of(1997, Month.JANUARY, 20))
        ));
        owners.save(PetOwner.of("Tintin", null,
                new Address("Belgium", "Bruxelles", "La Grand Place", "1/A"),
                Pet.of(PetType.DOG, "Wire Fox Terrier", "Snowy", LocalDate.of(1900, Month.AUGUST, 10))
        ));
    }

    @Test
    public void canApplyFilter() {

        final var fr = FilterRequest.builder()
                .text("byPetName", f -> f.eq(CaseSensitivity.IGNORE_CASE, "SNOWY"))
                .build();

        final var foundNames = owners.findAll(fr)
                .stream()
                .map(po -> po.firstName)
                .toList();

        Assertions.assertEquals(List.of("Tintin"), foundNames);

    }

    @Test
    public void canUseMultipleFilters() {
        final var fr = FilterRequest.builder()
                .inEnum("byPetType", PetType.DOG)
                .text("byPetName", f -> f.contains(CaseSensitivity.IGNORE_CASE, "O"))
                .localDate("byPetBirthDate", f -> f.gt(LocalDate.of(1800, Month.MARCH, 1)))
                .build();

        final var foundNames = owners.findAll(fr)
                .stream()
                .map(po -> po.firstName)
                .toList();

        Assertions.assertEquals(List.of("Dorothy", "Tintin"), foundNames);

    }

    @Test
    public void filtersCanBeCreatedFromMap() throws JsonProcessingException {
        final var filters = Map.of(
                "byPetType", new String[]{"DOG"},
                "byPetName", new String[]{"CONTAINS", "IGNORE_CASE", "O"},
                "byPetBirthDate", new String[]{"GT", "1800-03-01"}
        );

        final var foundNames = owners.findAll(new FilterRequest(filters))
                .stream()
                .map(po -> po.firstName)
                .toList();

        Assertions.assertEquals(List.of("Dorothy", "Tintin"), foundNames);
    }

    @Test
    public void filtersAreEasilyDeserializable() {
        JsonMapper om = new JsonMapper();
        final var mapType = new TypeReference<Map<String, String[]>>() {
        };
        final var filters = om.readValue(
                """
                {"byPetType":["DOG"],"byPetBirthDate":["GT","1800-03-01"],"byPetName":["CONTAINS","IGNORE_CASE","O"]}        
                """,
                mapType
        );

        final var foundNames = owners.findAll(new FilterRequest(filters))
                .stream()
                .map(po -> po.firstName)
                .toList();

        Assertions.assertEquals(List.of("Dorothy", "Tintin"), foundNames);
    }

    @Test
    public void canFilterJsonEmbeddables() {
        final var fr = FilterRequest.builder()
                .text("byState", f -> f.eq(CaseSensitivity.IGNORE_CASE, "KANSAS"))
                .build();

        final var foundNames = owners.findAll(fr)
                .stream()
                .map(po -> po.firstName)
                .toList();
        Assertions.assertEquals(List.of("Dorothy"), foundNames);
    }
}
