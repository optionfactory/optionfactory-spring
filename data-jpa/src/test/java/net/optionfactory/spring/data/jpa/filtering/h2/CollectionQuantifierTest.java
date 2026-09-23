package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import net.optionfactory.spring.data.jpa.filtering.TraversalFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable;
import net.optionfactory.spring.data.jpa.filtering.filters.Match;
import net.optionfactory.spring.data.jpa.filtering.filters.InstantCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.LocalDateCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.InList;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class CollectionQuantifierTest {

    @Entity
    public static class Tag {

        @Id
        public long id;
        public String label;
    }

    @Entity
    public static class Kennel {

        @Id
        public long id;
        public String city;
        @OneToMany(cascade = CascadeType.ALL)
        @JoinColumn(name = "kennelId")
        public List<Tag> badges;
    }

    /**
     * A custom filter traversing a collection: it implements {@link TraversalFilter} and states its
     * quantifier on its own traversal, so the adapter folds and negates it like any built-in one.
     */
    public static class TagAbsenceFilter implements TraversalFilter<String> {

        private final String name;
        private final Filters.Traversal traversal;

        public TagAbsenceFilter(Filterable annotation, EntityType<?> entity) {
            this.name = annotation.name();
            this.traversal = Filters.traversal(entity, annotation.name(), "tags.label", Match.NONE);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Filters.Traversal traversal() {
            return traversal;
        }

        @Override
        public Predicate condition(Root<?> root, Path<String> path, CriteriaBuilder builder, String[] values) {
            return builder.equal(path, values[0]);
        }
    }

    @Entity
    @Filterable(name = "customWithoutTag", filter = TagAbsenceFilter.class)
    @TextCompare(name = "byName", path = "name")
    @TextCompare(name = "byTag", path = "tags.label", match = Match.ANY)
    @TextCompare(name = "byKennelCity", path = "kennel.city")
    @TextCompare(name = "byKennelBadge", path = "kennel.badges.label", match = Match.ANY)
    @TextCompare(name = "withoutTag", path = "tags.label", match = Match.NONE)
    @TextCompare(name = "byTagPrefix", path = "tags.label", operators = TextCompare.Operator.STARTS_WITH, match = Match.ANY)
    public static class Pet {

        @Id
        public long id;
        public String name;
        @ManyToOne(cascade = CascadeType.ALL)
        public Kennel kennel;
        @OneToMany(cascade = CascadeType.ALL)
        @JoinColumn(name = "petId")
        public List<Tag> tags;
    }

    public interface PetsRepository extends JpaRepository<Pet, Long>, WhitelistFilteringRepository<Pet> {

    }

    @Inject
    private PetsRepository pets;

    @Inject
    private jakarta.persistence.EntityManagerFactory emf;

    @BeforeEach
    public void setup() {
        pets.save(pet(1, "EMPTY", kennel(1, "roma"), List.of()));
        pets.save(pet(2, "HAS-X", kennel(2, "roma", tag(10, "gold")), List.of(tag(1, "x"))));
        pets.save(pet(3, "HAS-X-AND-Y", kennel(3, "milano", tag(11, "silver")), List.of(tag(2, "x"), tag(3, "y"))));
        pets.save(pet(4, "HAS-Y", kennel(4, "milano"), List.of(tag(4, "y"))));
    }

    private List<String> matching(FilterRequest fr) {
        return pets.findAll(fr).stream().map(p -> p.name).sorted().toList();
    }

    @Test
    public void anyKeepsParentsWithAMatchingElement() {
        Assertions.assertEquals(List.of("HAS-X", "HAS-X-AND-Y"), matching(FilterRequest.builder().text("byTag", f -> f.eq("x")).build()));
    }

    @Test
    public void anyDropsParentsWithAnEmptyCollection() {
        Assertions.assertEquals(List.of("HAS-X-AND-Y", "HAS-Y"), matching(FilterRequest.builder().text("byTag", f -> f.eq("y")).build()));
    }

    @Test
    public void noneKeepsParentsWithoutAMatchingElement() {
        Assertions.assertEquals(List.of("EMPTY", "HAS-Y"), matching(FilterRequest.builder().text("withoutTag", f -> f.eq("x")).build()));
    }

    @Test
    public void anyAndNoneOverTheSameConditionPartitionTheRows() {
        final var any = matching(FilterRequest.builder().text("byTag", f -> f.eq("x")).build());
        final var none = matching(FilterRequest.builder().text("withoutTag", f -> f.eq("x")).build());
        Assertions.assertEquals(List.of("EMPTY", "HAS-X", "HAS-X-AND-Y", "HAS-Y"), java.util.stream.Stream.concat(any.stream(), none.stream()).sorted().toList());
    }

    @Test
    public void filtersSharingAQuantifierFoldIntoOneSubqueryOverTheSameElement() {
        // no single tag is both labelled exactly "y" and prefixed "x", so folding excludes HAS-X-AND-Y
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("byTagPrefix", f -> f.startsWith("x"))
                .build();
        Assertions.assertEquals(List.of(), matching(fr));
    }

    @Test
    public void filtersDisagreeingOnTheQuantifierGetASubqueryEach() {
        // HAS-X-AND-Y has an x; HAS-Y has no x. Only HAS-Y has a y and no x.
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("withoutTag", f -> f.eq("x"))
                .build();
        Assertions.assertEquals(List.of("HAS-Y"), matching(fr));
    }

    @Test
    public void aCollectionFilterComposesWithARootLevelSingularFilter() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("x"))
                .text("byKennelCity", f -> f.eq("milano"))
                .build();
        Assertions.assertEquals(List.of("HAS-X-AND-Y"), matching(fr));
    }

    @Test
    public void aCollectionFilterComposesWithAnInlineFilterSharingItsPrefix() {
        // byKennelCity is inline on the kennel join, byKennelBadge crosses kennel.badges into a subquery:
        // the shared `kennel` hop is navigated in both scopes
        final var fr = FilterRequest.builder()
                .text("byKennelCity", f -> f.eq("roma"))
                .text("byKennelBadge", f -> f.eq("gold"))
                .build();
        Assertions.assertEquals(List.of("HAS-X"), matching(fr));
    }

    @Test
    public void twoCollectionsComposeAsSeparateSubqueries() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("byKennelBadge", f -> f.eq("silver"))
                .build();
        Assertions.assertEquals(List.of("HAS-X-AND-Y"), matching(fr));
    }

    @Test
    public void anyAndNoneOverTheSameCollectionCompose() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("withoutTag", f -> f.eq("x"))
                .build();
        Assertions.assertEquals(List.of("HAS-Y"), matching(fr));
    }

    @Test
    public void aCustomTraversalFilterCarriesItsOwnQuantifier() {
        Assertions.assertEquals(List.of("EMPTY", "HAS-Y"), matching(new FilterRequest(java.util.Map.of("customWithoutTag", new String[]{"x"}))));
    }

    @Test
    public void aCustomTraversalFilterComposesWithABuiltInOne() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .with("customWithoutTag", "x")
                .build();
        Assertions.assertEquals(List.of("HAS-Y"), matching(fr));
    }

    @Test
    public void aQuantifierOnAPathWithoutACollectionIsRejected() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, () -> Filters.traversal(entity, "byName", "name", Match.NONE));
        Assertions.assertTrue(thrown.getMessage().contains("requires a path crossing a collection"), thrown.getMessage());
    }

    @Test
    public void anExplicitAnyIsAcceptedOnAPathWithoutACollection() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        Assertions.assertNull(Filters.traversal(entity, "byName", "name", Match.ANY).group());
    }

    @Test
    public void anUnstatedQuantifierIsAcceptedOnAPathWithoutACollection() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        final var traversal = Filters.traversal(entity, "byName", "name");
        Assertions.assertNull(traversal.group());
        Assertions.assertEquals(Match.ANY, traversal.match());
    }

    /// A filter over a collection written before quantifiers existed fails when the repository is
    /// built, instead of silently returning different rows.
    @Test
    public void anUnstatedQuantifierOnAPathCrossingACollectionIsRejected() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, () -> Filters.traversal(entity, "byTag", "tags.label", Match.UNSTATED));
        Assertions.assertTrue(thrown.getMessage().contains("its quantifier must be stated"), thrown.getMessage());
    }

    @Test
    public void aCustomFilterWithoutAQuantifierIsRejectedOnAPathCrossingACollection() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        Assertions.assertThrows(InvalidFilterConfiguration.class, () -> Filters.traversal(entity, "customByTag", "tags.label"));
    }

    @Test
    public void everyPathBasedFilterLeavesTheQuantifierUnstatedByDefault() throws Exception {
        for (final var annotation : List.of(TextCompare.class, NumberCompare.class, InEnum.class, InList.class, BooleanCompare.class, LocalDateCompare.class, InstantCompare.class)) {
            Assertions.assertEquals(Match.UNSTATED, annotation.getMethod("match").getDefaultValue(), annotation.getSimpleName());
        }
    }

    private static Tag tag(long id, String label) {
        final var t = new Tag();
        t.id = id;
        t.label = label;
        return t;
    }

    private static Kennel kennel(long id, String city, Tag... badges) {
        final var k = new Kennel();
        k.id = id;
        k.city = city;
        k.badges = List.of(badges);
        return k;
    }

    private static Pet pet(long id, String name, Kennel kennel, List<Tag> tags) {
        final var p = new Pet();
        p.id = id;
        p.name = name;
        p.kennel = kennel;
        p.tags = tags;
        return p;
    }
}
