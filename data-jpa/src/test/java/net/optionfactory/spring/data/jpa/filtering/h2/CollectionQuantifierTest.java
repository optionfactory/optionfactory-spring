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

    /// A custom filter traversing a collection: it implements [TraversalFilter] and states its
    /// quantifier on its own traversal, so the adapter folds and negates it like any built-in one.
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
        Assertions.assertEquals(List.of("HAS-X", "HAS-X-AND-Y"), matching(FilterRequest.builder().text("byTag", f -> f.eq("x")).build()), "ANY keeps the pets with at least one tag x");
    }

    @Test
    public void anyDropsParentsWithAnEmptyCollection() {
        Assertions.assertEquals(List.of("HAS-X-AND-Y", "HAS-Y"), matching(FilterRequest.builder().text("byTag", f -> f.eq("y")).build()), "ANY drops the pet with no tags at all");
    }

    @Test
    public void noneKeepsParentsWithoutAMatchingElement() {
        Assertions.assertEquals(List.of("EMPTY", "HAS-Y"), matching(FilterRequest.builder().text("withoutTag", f -> f.eq("x")).build()), "NONE keeps the pets with no tag x, the one with no tags included");
    }

    @Test
    public void anyAndNoneOverTheSameConditionPartitionTheRows() {
        final var any = matching(FilterRequest.builder().text("byTag", f -> f.eq("x")).build());
        final var none = matching(FilterRequest.builder().text("withoutTag", f -> f.eq("x")).build());
        Assertions.assertEquals(List.of("EMPTY", "HAS-X", "HAS-X-AND-Y", "HAS-Y"), java.util.stream.Stream.concat(any.stream(), none.stream()).sorted().toList(), "ANY and NONE over the same condition together return every pet exactly once");
    }

    /// No single tag is both labelled exactly `y` and prefixed `x`, so folding excludes `HAS-X-AND-Y`.
    @Test
    public void filtersSharingAQuantifierFoldIntoOneSubqueryOverTheSameElement() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("byTagPrefix", f -> f.startsWith("x"))
                .build();
        Assertions.assertEquals(List.of(), matching(fr), "folded filters must hold on the same tag, which no pet has");
    }

    /// `HAS-X-AND-Y` has an `x`; `HAS-Y` has no `x`. Only `HAS-Y` has a `y` and no `x`.
    @Test
    public void filtersDisagreeingOnTheQuantifierGetASubqueryEach() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("withoutTag", f -> f.eq("x"))
                .build();
        Assertions.assertEquals(List.of("HAS-Y"), matching(fr), "NONE and ANY get a subquery each: only the pet with a y and without an x is kept");
    }

    @Test
    public void aCollectionFilterComposesWithARootLevelSingularFilter() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("x"))
                .text("byKennelCity", f -> f.eq("milano"))
                .build();
        Assertions.assertEquals(List.of("HAS-X-AND-Y"), matching(fr), "a collection filter and a singular association filter both apply");
    }

    /// `byKennelCity` is inline on the kennel join, `byKennelBadge` crosses `kennel.badges` into a
    /// subquery: the shared `kennel` hop is navigated in both scopes.
    @Test
    public void aCollectionFilterComposesWithAnInlineFilterSharingItsPrefix() {
        final var fr = FilterRequest.builder()
                .text("byKennelCity", f -> f.eq("roma"))
                .text("byKennelBadge", f -> f.eq("gold"))
                .build();
        Assertions.assertEquals(List.of("HAS-X"), matching(fr), "the kennel hop is navigated both inline and inside the badges subquery");
    }

    @Test
    public void twoCollectionsComposeAsSeparateSubqueries() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("byKennelBadge", f -> f.eq("silver"))
                .build();
        Assertions.assertEquals(List.of("HAS-X-AND-Y"), matching(fr), "filters over two collections each get their own subquery");
    }

    @Test
    public void anyAndNoneOverTheSameCollectionCompose() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .text("withoutTag", f -> f.eq("x"))
                .build();
        Assertions.assertEquals(List.of("HAS-Y"), matching(fr), "ANY over y and NONE over x on the same collection both apply");
    }

    @Test
    public void aCustomTraversalFilterCarriesItsOwnQuantifier() {
        Assertions.assertEquals(List.of("EMPTY", "HAS-Y"), matching(new FilterRequest(java.util.Map.of("customWithoutTag", new String[]{"x"}))), "a custom traversal filter applies its own NONE quantifier");
    }

    @Test
    public void aCustomTraversalFilterComposesWithABuiltInOne() {
        final var fr = FilterRequest.builder()
                .text("byTag", f -> f.eq("y"))
                .with("customWithoutTag", "x")
                .build();
        Assertions.assertEquals(List.of("HAS-Y"), matching(fr), "a custom traversal filter composes with a built-in one");
    }

    @Test
    public void aQuantifierOnAPathWithoutACollectionIsRejected() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, () -> Filters.traversal(entity, "byName", "name", Match.NONE), "NONE on a path crossing no collection is rejected");
        Assertions.assertTrue(thrown.getMessage().contains("requires a path crossing a collection"), thrown.getMessage());
    }

    @Test
    public void anExplicitAnyIsAcceptedOnAPathWithoutACollection() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        Assertions.assertNull(Filters.traversal(entity, "byName", "name", Match.ANY).group(), "an explicit ANY on a path crossing no collection opens no subquery");
    }

    @Test
    public void anUnstatedQuantifierIsAcceptedOnAPathWithoutACollection() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        final var traversal = Filters.traversal(entity, "byName", "name");
        Assertions.assertNull(traversal.group(), "an unstated quantifier on a path crossing no collection opens no subquery");
        Assertions.assertEquals(Match.ANY, traversal.match(), "an unstated quantifier on a path crossing no collection reads as ANY");
    }

    /// A filter over a collection written before quantifiers existed fails when the repository is
    /// built, instead of silently returning different rows.
    @Test
    public void anUnstatedQuantifierOnAPathCrossingACollectionIsRejected() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        final var thrown = Assertions.assertThrows(InvalidFilterConfiguration.class, () -> Filters.traversal(entity, "byTag", "tags.label", Match.UNSTATED), "an unstated quantifier on a path crossing a collection is rejected");
        Assertions.assertTrue(thrown.getMessage().contains("its quantifier must be stated"), thrown.getMessage());
    }

    @Test
    public void aCustomFilterWithoutAQuantifierIsRejectedOnAPathCrossingACollection() {
        final var entity = emf.getMetamodel().entity(Pet.class);
        Assertions.assertThrows(InvalidFilterConfiguration.class, () -> Filters.traversal(entity, "customByTag", "tags.label"), "the quantifier-less traversal is rejected on a path crossing a collection");
    }

    @Test
    public void everyPathBasedFilterLeavesTheQuantifierUnstatedByDefault() throws Exception {
        for (final var annotation : List.of(TextCompare.class, NumberCompare.class, InEnum.class, InList.class, BooleanCompare.class, LocalDateCompare.class, InstantCompare.class)) {
            Assertions.assertEquals(Match.UNSTATED, annotation.getMethod("match").getDefaultValue(), annotation.getSimpleName() + " leaves the quantifier unstated by default");
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
