package net.optionfactory.spring.data.jpa.filtering.h2.filters.spi;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@Transactional
public class FiltersTest {

    @Autowired
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
