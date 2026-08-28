package net.optionfactory.spring.data.jpa.filtering.h2.filters;

import jakarta.inject.Inject;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class FiltersTest {

    @Entity
    public static class RootAgg {

        @Id
        public long id;
        @ManyToOne
        public Branch b;
    }

    @Entity
    public static class Branch {

        @Id
        public long id;
        @ManyToOne
        public Leaf c;
    }

    @Entity
    public static class Leaf {

        @Id
        public long id;
        @Embedded
        public Embed i;

    }

    @Embeddable
    public static class Embed {

        public long n;
    }

    public interface RootsRepository extends JpaRepository<RootAgg, Long>, WhitelistFilteringRepository<RootAgg> {
    }

    @Inject
    private RootsRepository repository;

    @Test
    public void canSpecifyEmptyTraversal() {
        final Specification<RootAgg> specification = new Specification<RootAgg>() {
            @Override
            public Predicate toPredicate(Root<RootAgg> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final var ts = Filters.traversal(root.getModel(), "myFilter", "");
                final Path<?> path = Filters.path(root, "myFilter", ts);
                Assertions.assertEquals(RootAgg.class, path.getJavaType());
                return null;
            }
        };
        repository.findOne(specification, FilterRequest.unfiltered());
    }

    @Test
    public void canTraversePropertyChain() {
        final Specification<RootAgg> specification = new Specification<RootAgg>() {
            @Override
            public Predicate toPredicate(Root<RootAgg> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final var ts = Filters.traversal(root.getModel(), "myFilter", "b.c.i.n");
                final Expression<Object> path = Filters.path(root, "myFilter", ts);
                Assertions.assertEquals(Long.class, path.getJavaType());
                return null;
            }
        };
        repository.findOne(specification, FilterRequest.unfiltered());
    }

    @Test
    public void throwsWhenNonExistantPropertyIsReferencedInPropertyChain() {
        final Specification<RootAgg> specification = new Specification<RootAgg>() {
            @Override
            public Predicate toPredicate(Root<RootAgg> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final var ts = Filters.traversal(root.getModel(), "myFilter", "b.x.id");
                final Expression<Object> nonExistant = Filters.path(root, "myFilter", ts);
                return null;
            }
        };

        Assertions.assertThrows(InvalidDataAccessApiUsageException.class, () -> {
            repository.findOne(specification, FilterRequest.unfiltered());
        });
    }

    @Test
    public void ensureAcceptsTruePrecondition() {
        Filters.ensure(true, null, "name", "");
    }

    @Test
    public void ensureThrowsOnFalsePrecondition() {
        Assertions.assertThrows(InvalidFilterRequest.class, () -> {
            Filters.ensure(false, null, "name", "");
        });
    }

}
