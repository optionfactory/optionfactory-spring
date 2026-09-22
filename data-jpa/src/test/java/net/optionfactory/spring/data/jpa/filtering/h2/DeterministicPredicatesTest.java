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
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringSpecificationAdapter;
import net.optionfactory.spring.data.jpa.filtering.filters.FilterTraversal;
import net.optionfactory.spring.data.jpa.filtering.filters.TextCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
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
    @TextCompare(name = "byLeafA", path = "leaves.a")
    @TextCompare(name = "byLeafB", path = "leaves.b")
    public static class Root {

        @Id
        public long id;
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
        final var first = Filters.traversal(entity, "byLeafA", "leaves.a");
        final var second = Filters.traversal(entity, "byLeafA", "leaves.a");
        Assertions.assertEquals(first.group(), second.group());
        Assertions.assertEquals("leaves!byLeafA#ANY", first.group());
    }

    @Test
    public void isolatedSubqueryGroupsStayDistinctPerFilter() {
        final var entity = emf.getMetamodel().entity(Root.class);
        Assertions.assertNotEquals(
                Filters.traversal(entity, "byLeafA", "leaves.a").group(),
                Filters.traversal(entity, "byLeafB", "leaves.b").group());
    }

    @Test
    public void predicatesAreEmittedInFilterNameOrderWhateverTheRequestOrder() {
        Assertions.assertEquals(rendered(orderedRequest("byLeafB", "byLeafA")), rendered(orderedRequest("byLeafA", "byLeafB")));
    }

    /**
     * {@code reuse = false} puts each filter in its own EXISTS, so both are rendered and
     * their order is observable in the generated query.
     */
    private String rendered(FilterRequest fr) {
        try (final var em = emf.createEntityManager()) {
            final var ei = JpaEntityInformationSupport.getEntityInformation(Root.class, em);
            final var builder = em.getCriteriaBuilder();
            final var query = builder.createQuery(Root.class);
            final var root = query.from(Root.class);
            query.where(new WhitelistFilteringSpecificationAdapter<Root>(fr, Repositories.allowedFilters(ei, em)).toPredicate(root, query, builder));
            return em.createQuery(query.select(root)).unwrap(org.hibernate.query.Query.class).getQueryString();
        }
    }

    private static FilterRequest orderedRequest(String first, String second) {
        final Map<String, String[]> filters = new LinkedHashMap<>();
        filters.put(first, TextCompare.Filter.INSTANCE.eq(first));
        filters.put(second, TextCompare.Filter.INSTANCE.eq(second));
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
        Assertions.assertEquals(1, roots.findAll(fr).size());
        Assertions.assertEquals(0, roots.findAll(FilterRequest.builder().text("byLeafA", f -> f.eq("nope")).build()).size());
    }
}
