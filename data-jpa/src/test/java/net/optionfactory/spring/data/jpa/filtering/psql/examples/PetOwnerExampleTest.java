package net.optionfactory.spring.data.jpa.filtering.psql.examples;

import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.filtering.psql.HibernateOnPsqlTestConfig;
import net.optionfactory.spring.data.jpa.filtering.psql.examples.Pet.PetType;
import net.optionfactory.spring.data.jpa.filtering.psql.examples.PetOwner.Address;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnPsqlTestConfig.class)
public class PetOwnerExampleTest {

    @Inject
    private PetOwnersRepository owners;

    @Before
    @Transactional
    public void setup() {
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
    @Transactional
    public void canApplyFilter() {

        final var fr = FilterRequest.builder()
                .text("byPetName", f -> f.eq(CaseSensitivity.IGNORE_CASE, "SNOWY"))
                .build();

        final var foundNames = owners.findAll(fr)
                .stream()
                .map(po -> po.firstName)
                .toList();

        Assert.assertEquals(List.of("Tintin"), foundNames);

    }

    @Test
    @Transactional
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

        Assert.assertEquals(List.of("Dorothy", "Tintin"), foundNames);

    }

    @Test
    @Transactional
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

        Assert.assertEquals(List.of("Dorothy", "Tintin"), foundNames);
    }

    @Test
    @Transactional
    public void filtersAreEasilyDeserializable(){
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

        Assert.assertEquals(List.of("Dorothy", "Tintin"), foundNames);
    }

    @Test
    @Transactional
    public void canFilterJsonEmbeddables() {
        final var fr = FilterRequest.builder()
                .text("byState", f -> f.eq(CaseSensitivity.IGNORE_CASE, "KANSAS"))
                .build();

        final var foundNames = owners.findAll(fr)
                .stream()
                .map(po -> po.firstName)
                .toList();
        Assert.assertEquals(List.of("Dorothy"), foundNames);
    }
}
