package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.criteria.JoinType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.filters.Match;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringSpecificationAdapter;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class DeterministicPredicatesTest {

    @Entity
    public static class Leaf {

        @Id
        public long id;
        public Long rootId;
        public String a;
        public String b;
    }

    @Entity
    @FilterTraversal(path = "leaves", joinType = JoinType.INNER, reuse = false)
    @TextCompare(name = "byLeafA", path = "leaves.a", match = Match.ANY)
    @TextCompare(name = "byLeafB", path = "leaves.b", match = Match.ANY)
    @TextCompare(name = "byRootA", path = "a")
    @TextCompare(name = "byRootB", path = "b")
    public static class Root {

        @Id
        public long id;
        public String a;
        public String b;
        @OneToMany(cascade = CascadeType.ALL)
        @JoinColumn(name = "rootId")
        public List<Leaf> leaves;
    }

    public interface RootsRepository extends JpaRepository<Root, Long>, WhitelistFilteringRepository<Root> {

    }

    @Inject
    private RootsRepository roots;

    @Inject
    private EntityManagerFactory emf;

    @Test
    public void anUnfilteredRequestIsUnrestricted() {
        try (final var em = emf.createEntityManager()) {
            final var ei = JpaEntityInformationSupport.getEntityInformation(Root.class, em);
            final var builder = em.getCriteriaBuilder();
            final var query = builder.createQuery(Root.class);
            final var root = query.from(Root.class);
            final var adapter = new WhitelistFilteringSpecificationAdapter<Root>(FilterRequest.unfiltered(), Repositories.allowedFilters(ei, em));
            Assertions.assertNull(adapter.toPredicate(root, query, builder), "an unfiltered request should add no restriction at all");
        }
    }

    @Test
    public void isolatedSubqueryGroupsAreStableAcrossResolutions() {
        final var entity = emf.getMetamodel().entity(Root.class);
        final var first = Filters.traversal(entity, "byLeafA", "leaves.a", Match.ANY);
        final var second = Filters.traversal(entity, "byLeafA", "leaves.a", Match.ANY);
        Assertions.assertEquals(first.group(), second.group(), "an isolated group token is the same at every resolution");
        Assertions.assertEquals("leaves!byLeafA#ANY", first.group(), "an isolated group token is derived from path, filter name and quantifier");
    }

    @Test
    public void isolatedSubqueryGroupsStayDistinctPerFilter() {
        final var entity = emf.getMetamodel().entity(Root.class);
        Assertions.assertNotEquals(
                Filters.traversal(entity, "byLeafA", "leaves.a", Match.ANY).group(),
                Filters.traversal(entity, "byLeafB", "leaves.b", Match.ANY).group(), "isolated groups of different filters are distinct");
    }

    @Test
    public void predicatesAreEmittedInFilterNameOrderWhateverTheRequestOrder() {
        final var requestedBFirst = rendered(orderedRequest("byRootB", "byRootA", "byLeafB", "byLeafA"));
        final var requestedAFirst = rendered(orderedRequest("byLeafA", "byLeafB", "byRootA", "byRootB"));
        Assertions.assertEquals(requestedAFirst, requestedBFirst, "the same filters render the same query whatever the request order");
        assertRenderedBefore(requestedBFirst, "byRootA", "byRootB");
        assertRenderedBefore(requestedBFirst, "byLeafA", "byLeafB");
    }

    private static void assertRenderedBefore(String rendered, String first, String second) {
        final var f = rendered.indexOf("'" + first + "'");
        final var s = rendered.indexOf("'" + second + "'");
        Assertions.assertTrue(f != -1 && s != -1, String.format("both %s and %s are rendered in the query: %s", first, second, rendered));
        Assertions.assertTrue(f < s, String.format("%s is rendered before %s, in filter name order: %s", first, second, rendered));
    }

    /// The filters on the root's own properties are conjoined directly, while `reuse = false` puts
    /// each filter on the leaves in its own EXISTS: the order of both kinds is observable in the
    /// generated query.
    ///
    /// The query is rendered from the criteria tree as HQL: `Query.getQueryString()` is the
    /// constant `<criteria>` for a criteria query, and would compare equal whatever the order.
    private String rendered(FilterRequest fr) {
        try (final var em = emf.createEntityManager()) {
            final var ei = JpaEntityInformationSupport.getEntityInformation(Root.class, em);
            final var builder = em.getCriteriaBuilder();
            final var query = builder.createQuery(Root.class);
            final var root = query.from(Root.class);
            query.where(new WhitelistFilteringSpecificationAdapter<Root>(fr, Repositories.allowedFilters(ei, em)).toPredicate(root, query, builder));
            return ((SqmSelectStatement<Root>) query.select(root)).toHqlString();
        }
    }

    private static FilterRequest orderedRequest(String... names) {
        final Map<String, String[]> filters = new LinkedHashMap<>();
        for (final var name : names) {
            filters.put(name, TextCompare.Filter.INSTANCE.eq(name));
        }
        return new FilterRequest(filters);
    }

    @Test
    public void filtersStillMatchWhenFoldedIntoIsolatedSubqueries() {
        final var root = new Root();
        root.id = 1;
        final var leaf = new Leaf();
        leaf.id = 1;
        leaf.a = "x";
        leaf.b = "y";
        root.leaves = List.of(leaf);
        roots.save(root);

        final var fr = FilterRequest.builder()
                .text("byLeafA", f -> f.eq("x"))
                .text("byLeafB", f -> f.eq("y"))
                .build();
        Assertions.assertEquals(1, roots.findAll(fr).size(), "both isolated subqueries match the same leaf");
        Assertions.assertEquals(0, roots.findAll(FilterRequest.builder().text("byLeafA", f -> f.eq("nope")).build()).size(), "a non-matching filter excludes the root");
    }
}
