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
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class FiltersTest {

    @Entity
    public static class EntityA {

        @Id
        public long id;

        @ManyToOne
        public EntityB b;
    }

    @Entity
    public static class EntityB {

        @Id
        public long id;

        @ManyToOne
        public EntityC c;
    }

    @Entity
    public static class EntityC {

        @Id
        public long id;

        @Embedded
        public Inner i;

    }

    @Embeddable
    public static class Inner {

        public long n;
    }

    public interface EntityARepository extends JpaRepository<EntityA, Long>, WhitelistFilteringRepository<EntityA> {
    }

    @Inject
    private EntityARepository repository;

    @Test
    public void canSpecifyEmptyTraversal() {
        final Specification<EntityA> specification = new Specification<EntityA>() {
            @Override
            public Predicate toPredicate(Root<EntityA> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final var ts = Filters.traversal(root.getModel(), "myFilter", "");
                final Path<?> path = Filters.path(root, "myFilter", ts);
                Assertions.assertEquals(EntityA.class, path.getJavaType());
                return null;
            }
        };
        repository.findOne(specification, FilterRequest.unfiltered());
    }

    @Test
    public void canTraversePropertyChain() {
        final Specification<EntityA> specification = new Specification<EntityA>() {
            @Override
            public Predicate toPredicate(Root<EntityA> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
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
        final Specification<EntityA> specification = new Specification<EntityA>() {
            @Override
            public Predicate toPredicate(Root<EntityA> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
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
