package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.BooleanCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.Filterable;
import net.optionfactory.spring.data.jpa.filtering.filters.InEnum;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.CaseSensitivity;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare.Operator;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.CustomFilter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/// The whitelisting contract of the repository: only what the entity whitelists can be requested,
/// in the ways it whitelists, and the client's values are checked before they reach a query.
@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class WhitelistContractTest {

    public enum Kind {
        DOG, CAT
    }

    @Entity
    @TextCompare(name = "byName", path = "name", operators = {Operator.EQ, Operator.CONTAINS}, caseSensitivity = CaseSensitivity.CASE_SENSITIVE)
    @TextCompare(name = "anyName", path = "name")
    @NumberCompare(name = "byWeight", path = "weight")
    @InEnum(name = "byKind", path = "kind", type = Kind.class)
    @InEnum(name = "byKindOrUnknown", path = "kind", type = Kind.class, nullable = true)
    @BooleanCompare(name = "byVaccinated", path = "vaccinated")
    @Sortable(name = "byName", path = "name")
    @Sortable(name = "byWeight", path = "weight")
    public static class WhitelistedAnimal {

        @Id
        public long id;
        public String name;
        public Integer weight;
        @Enumerated(EnumType.STRING)
        public Kind kind;
        public Boolean vaccinated;
    }

    public interface WhitelistedAnimalRepository extends JpaRepository<WhitelistedAnimal, Long>, WhitelistFilteringRepository<WhitelistedAnimal> {
    }

    /// Has no repository of its own: its filters are created explicitly, so the context still
    /// starts.
    @Entity
    @TextCompare(name = "dup", path = "name")
    @NumberCompare(name = "dup", path = "weight")
    public static class DuplicateFilterNames {

        @Id
        public long id;
        public String name;
        public Integer weight;
    }

    public static class UnconstructibleFilter extends CustomFilter {

        public UnconstructibleFilter(Filterable annotation, String unavailable) {
            super(annotation);
        }

        @Override
        public Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder builder, String[] values) {
            return builder.conjunction();
        }
    }

    /// Has no repository of its own: its filters are created explicitly, so the context still
    /// starts.
    @Entity
    @Filterable(name = "broken", filter = UnconstructibleFilter.class)
    public static class WithUnconstructibleFilter {

        @Id
        public long id;
    }

    @Inject
    private WhitelistedAnimalRepository animals;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    public void setup() {
        animals.saveAll(List.of(
                animal(1, "rex", 10, Kind.DOG, true),
                animal(2, "50%_off", 20, Kind.CAT, false),
                animal(3, "50 percent", null, null, null),
                animal(4, "felix", 30, Kind.CAT, true)
        ));
    }

    @Test
    public void aFilterThatIsNotWhitelistedIsRejected() {
        final var request = FilterRequest.unfiltered().with("byColor", "black");
        final var thrown = Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> animals.findAll(request), "spring wraps the rejection of an unknown filter");
        final var rejected = Assertions.assertInstanceOf(InvalidFilterRequest.class, thrown.getCause(), "the cause is the InvalidFilterRequest");
        Assertions.assertEquals("byColor", rejected.filter, "the rejection names the requested filter");
        Assertions.assertEquals("filter not configured in root object", rejected.reason, "the reason does not reveal the entity");
    }

    @Test
    public void anOperatorThatIsNotWhitelistedIsRejected() {
        final var request = FilterRequest.builder().text("byName", f -> f.startsWith("re")).build();
        final var rejected = rejection(request);
        Assertions.assertTrue(rejected.reason.startsWith("operator STARTS_WITH not whitelisted"), "only EQ and CONTAINS are whitelisted on byName, got: " + rejected.reason);
    }

    @Test
    public void aCaseSensitivityThatIsNotWhitelistedIsRejected() {
        final var request = FilterRequest.builder().text("byName", f -> f.eq(CaseSensitivity.IGNORE_CASE, "REX")).build();
        final var rejected = rejection(request);
        Assertions.assertTrue(rejected.reason.startsWith("mode IGNORE_CASE not whitelisted"), "only CASE_SENSITIVE is whitelisted on byName, got: " + rejected.reason);
    }

    @Test
    public void likeOperandsMatchWildcardsLiterally() {
        final var request = FilterRequest.builder().text("anyName", f -> f.contains("%_")).build();
        Assertions.assertEquals(Set.of(2L), ids(animals.findAll(request)), "% and _ in the operand only match themselves");
    }

    @Test
    public void betweenBoundsAreIncludedAndMayComeInEitherOrder() {
        final var request = FilterRequest.builder().number("byWeight", f -> f.between(30, 20)).build();
        Assertions.assertEquals(Set.of(2L, 4L), ids(animals.findAll(request)), "the reversed bounds are swapped, and both are included");
    }

    @Test
    public void aNullEnumValueIsRejectedUnlessTheFilterIsNullable() {
        final var request = FilterRequest.builder().inEnum("byKind", Kind.DOG, null).build();
        final var rejected = rejection(request);
        Assertions.assertEquals("null enum filter values is not whitelisted", rejected.reason, "byKind is not nullable");
    }

    @Test
    public void aNullEnumValueMatchesNullRowsOnANullableFilter() {
        final var request = FilterRequest.builder().inEnum("byKindOrUnknown", Kind.DOG, null).build();
        Assertions.assertEquals(Set.of(1L, 3L), ids(animals.findAll(request)), "the null value adds the rows without a kind to the DOG ones");
    }

    @Test
    public void aBooleanComparisonNeverMatchesNullRows() {
        final var request = FilterRequest.builder().bool("byVaccinated", f -> f.neq(true)).build();
        Assertions.assertEquals(Set.of(2L), ids(animals.findAll(request)), "NEQ true keeps the false rows only, not the NULL one");
    }

    @Test
    public void aNullTruthValueComparesWithNull() {
        final var isNull = FilterRequest.builder().bool("byVaccinated", f -> f.eq((Boolean) null)).build();
        final var isNotNull = FilterRequest.builder().bool("byVaccinated", f -> f.neq((Boolean) null)).build();
        Assertions.assertEquals(Set.of(3L), ids(animals.findAll(isNull)), "EQ null keeps the NULL rows");
        Assertions.assertEquals(Set.of(1L, 2L, 4L), ids(animals.findAll(isNotNull)), "NEQ null keeps the rows that are not NULL");
    }

    @Test
    public void inheritedSortedFindAllSortsBySorterName() {
        final var sorted = animals.findAll(Sort.by(Sort.Order.desc("byWeight").nullsLast()));
        Assertions.assertEquals(List.of(4L, 2L, 1L, 3L), sorted.stream().map(a -> a.id).toList(), "the inherited findAll(Sort) resolves the sorter name");
    }

    @Test
    public void inheritedSortedFindAllRejectsAPropertyThatIsNotASorter() {
        final var thrown = Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> animals.findAll(Sort.by("weight")), "an entity property is not a sorter");
        final var rejected = Assertions.assertInstanceOf(InvalidSortRequest.class, thrown.getCause(), "the cause is the InvalidSortRequest");
        Assertions.assertEquals("weight", rejected.sorter, "the rejection names the requested sorter");
    }

    @Test
    public void sortHonoursTheRequestedNullHandling() {
        final var nullsFirst = animals.findAll(FilterRequest.unfiltered(), Sort.by(Sort.Order.asc("byWeight").nullsFirst()));
        final var nullsLast = animals.findAll(FilterRequest.unfiltered(), Sort.by(Sort.Order.asc("byWeight").nullsLast()));
        Assertions.assertEquals(List.of(3L, 1L, 2L, 4L), nullsFirst.stream().map(a -> a.id).toList(), "NULLS_FIRST puts the row without a weight first");
        Assertions.assertEquals(List.of(1L, 2L, 4L, 3L), nullsLast.stream().map(a -> a.id).toList(), "NULLS_LAST puts the row without a weight last");
    }

    @Test
    public void countAppliesTheFilters() {
        final var cats = FilterRequest.builder().inEnum("byKind", Kind.CAT).build();
        Assertions.assertEquals(2, animals.count(cats), "two animals are cats");
        Assertions.assertEquals(4, animals.count(FilterRequest.unfiltered()), "an unfiltered count counts every animal");
    }

    @Test
    public void aPageIsCountedWithTheFiltersAndSortedBySorterName() {
        final var cats = FilterRequest.builder().inEnum("byKind", Kind.CAT).build();
        final var page = animals.findAll(cats, PageRequest.of(0, 1, Sort.by("byName")));
        Assertions.assertEquals(2, page.getTotalElements(), "the total counts the filtered animals only");
        Assertions.assertEquals(List.of(2L), page.getContent().stream().map(a -> a.id).toList(), "the first page holds the first cat by name");
    }

    @Test
    public void findOneReturnsTheSingleMatchOrEmpty() {
        final var rex = FilterRequest.builder().text("byName", f -> f.eq("rex")).build();
        final var nobody = FilterRequest.builder().text("byName", f -> f.eq("nobody")).build();
        Assertions.assertEquals(1L, animals.findOne(rex).orElseThrow().id, "the single match is returned");
        Assertions.assertTrue(animals.findOne(nobody).isEmpty(), "no match is empty");
    }

    @Test
    public void findOneFailsWhenMoreThanOneMatches() {
        final var cats = FilterRequest.builder().inEnum("byKind", Kind.CAT).build();
        Assertions.assertThrows(IncorrectResultSizeDataAccessException.class, () -> animals.findOne(cats), "two matches are not one");
    }

    @Test
    public void twoFiltersWithTheSameNameFailTheWhitelisting() {
        final var ei = JpaEntityInformationSupport.getEntityInformation(DuplicateFilterNames.class, em);
        Assertions.assertThrows(IllegalStateException.class, () -> Repositories.allowedFilters(ei, em), "filter names must be unique on an entity");
    }

    @Test
    public void aFilterWithoutASuitableConstructorFailsTheWhitelisting() {
        final var ei = JpaEntityInformationSupport.getEntityInformation(WithUnconstructibleFilter.class, em);
        final var thrown = Assertions.assertThrows(IllegalStateException.class, () -> Repositories.allowedFilters(ei, em), "a constructor parameter the repository cannot provide is rejected");
        Assertions.assertTrue(thrown.getMessage().startsWith("No suitable public constructor"), "the message says no constructor fits, got: " + thrown.getMessage());
    }

    private InvalidFilterRequest rejection(FilterRequest request) {
        final var thrown = Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> animals.findAll(request), "spring wraps the rejection of the request");
        return Assertions.assertInstanceOf(InvalidFilterRequest.class, thrown.getCause(), "the cause is the InvalidFilterRequest");
    }

    private static Set<Long> ids(List<WhitelistedAnimal> found) {
        return found.stream().map(a -> a.id).collect(Collectors.toSet());
    }

    private static WhitelistedAnimal animal(long id, String name, Integer weight, Kind kind, Boolean vaccinated) {
        final var a = new WhitelistedAnimal();
        a.id = id;
        a.name = name;
        a.weight = weight;
        a.kind = kind;
        a.vaccinated = vaccinated;
        return a;
    }
}
