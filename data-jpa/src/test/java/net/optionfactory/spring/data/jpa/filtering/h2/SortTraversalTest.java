package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringSpecificationAdapter;
import net.optionfactory.spring.data.jpa.filtering.WhitelistSortingSpecificationAdapter;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortConfiguration;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Sorters;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class SortTraversalTest {

    @Entity
    public static class Owner {

        @Id
        public long id;
        public String name;
    }

    @Entity
    public static class Tag {

        @Id
        public long id;
        public Long petId;
        public String label;
    }

    @Entity
    @TextCompare(name = "byOwnerName", path = "owner.name")
    @Sortable(name = "byOwnerName", path = "owner.name")
    @Sortable(name = "byName", path = "name")
    public static class Pet {

        @Id
        public long id;
        public String name;
        @ManyToOne(cascade = CascadeType.ALL)
        public Owner owner;
        @OneToMany(cascade = CascadeType.ALL)
        @JoinColumn(name = "petId")
        public List<Tag> tags;
    }

    /**
     * Has no repository of its own: its sorters are resolved explicitly, so the
     * context still starts.
     */
    @Entity
    @Sortable(name = "byTagLabel", path = "tags.label")
    @Sortable(name = "byMissing", path = "nonExistent")
    public static class UnsortablePet {

        @Id
        public long id;
        @OneToMany(cascade = CascadeType.ALL)
        @JoinColumn(name = "unsortablePetId")
        public List<Tag> tags;
    }

    public interface PetsRepository extends JpaRepository<Pet, Long>, WhitelistFilteringRepository<Pet> {

    }

    @Inject
    private PetsRepository pets;

    @Inject
    private EntityManagerFactory emf;

    @BeforeEach
    public void setup() {
        pets.save(pet(1, "a", owner(1, "charlie")));
        pets.save(pet(2, "b", owner(2, "alice")));
        pets.save(pet(3, "c", owner(3, "bob")));
    }

    @Test
    public void canSortThroughASingularAssociation() {
        final var sorted = pets.findAll(FilterRequest.unfiltered(), Sort.by("byOwnerName"));
        Assertions.assertEquals(List.of("b", "c", "a"), sorted.stream().map(p -> p.name).toList());
    }

    @Test
    public void canSortThroughASingularAssociationIgnoringCase() {
        final var sorted = pets.findAll(FilterRequest.unfiltered(), Sort.by(Sort.Order.desc("byOwnerName").ignoreCase()));
        Assertions.assertEquals(List.of("a", "c", "b"), sorted.stream().map(p -> p.name).toList());
    }

    @Test
    public void sortingReusesTheJoinCreatedByAFilterOnTheSameAssociation() {
        try (final var em = emf.createEntityManager()) {
            final var ei = JpaEntityInformationSupport.getEntityInformation(Pet.class, em);
            final var allowedFilters = Repositories.allowedFilters(ei, em);
            final var allowedSorters = Repositories.allowedSorters(ei, em);

            final var builder = em.getCriteriaBuilder();
            final var query = builder.createQuery(Pet.class);
            final var root = query.from(Pet.class);

            final var fr = FilterRequest.builder().text("byOwnerName", f -> f.eq("alice")).build();
            new WhitelistFilteringSpecificationAdapter<Pet>(fr, allowedFilters).toPredicate(root, query, builder);
            new WhitelistSortingSpecificationAdapter<Pet>(Sort.by("byOwnerName"), allowedSorters).toPredicate(root, query, builder);

            Assertions.assertEquals(1, root.getJoins().size());
        }
    }

    @Test
    public void sortersAreResolvedWhenTheRepositoryIsBuilt() {
        try (final var em = emf.createEntityManager()) {
            final var ei = JpaEntityInformationSupport.getEntityInformation(UnsortablePet.class, em);
            final var thrown = Assertions.assertThrows(InvalidSortConfiguration.class, () -> Repositories.allowedSorters(ei, em));
            Assertions.assertTrue(thrown.getMessage().contains("in sorter by"), thrown.getMessage());
        }
    }

    @Test
    public void sortablePathCrossingACollectionIsRejected() {
        final var entity = emf.getMetamodel().entity(UnsortablePet.class);
        final var thrown = Assertions.assertThrows(InvalidSortConfiguration.class, () -> Sorters.traversal(entity, "byTagLabel", "tags.label"));
        Assertions.assertTrue(thrown.getMessage().contains("crosses a collection"), thrown.getMessage());
    }

    @Test
    public void unresolvableSortablePathIsRejected() {
        final var entity = emf.getMetamodel().entity(UnsortablePet.class);
        final var thrown = Assertions.assertThrows(InvalidSortConfiguration.class, () -> Sorters.traversal(entity, "byMissing", "nonExistent"));
        Assertions.assertTrue(thrown.getMessage().contains("cannot resolve path"), thrown.getMessage());
    }

    @Test
    public void unwhitelistedSorterIsRejected() {
        // spring's persistence exception translation wraps the InvalidSortRequest, an IllegalArgumentException
        final var thrown = Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> pets.findAll(FilterRequest.unfiltered(), Sort.by("byTagLabel")));
        Assertions.assertInstanceOf(InvalidSortRequest.class, thrown.getCause());
        Assertions.assertTrue(thrown.getMessage().contains("sorter not configured"), thrown.getMessage());
    }

    private static Owner owner(long id, String name) {
        final var o = new Owner();
        o.id = id;
        o.name = name;
        return o;
    }

    private static Pet pet(long id, String name, Owner owner) {
        final var p = new Pet();
        p.id = id;
        p.name = name;
        p.owner = owner;
        return p;
    }
}
