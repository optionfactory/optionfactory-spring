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
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateOnH2TestConfig.class)
@Transactional
public class FiltersTest {

    @Autowired
    private EntityARepository repository;

    @Test
    public void canSpecifyEmptyTraversal() {
        final Specification<EntityA> specification = new Specification<EntityA>() {
            @Override
            public Predicate toPredicate(Root<EntityA> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final Traversal ts = Filters.traversal(null, root.getModel(), "");
                final Path<?> path = Filters.path("myFilter", root, ts);
                Assert.assertEquals(EntityA.class, path.getJavaType());
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
                final Traversal ts = Filters.traversal(null, root.getModel(), "b.c.i.n");
                final Expression<Object> path = Filters.path("myFilter", root, ts);
                Assert.assertEquals(Long.class, path.getJavaType());
                return null;
            }
        };
        repository.findOne(specification, FilterRequest.unfiltered());
    }

    @Test(expected = InvalidFilterConfiguration.class)
    public void throwsWhenNonExistantPropertyIsReferencedInPropertyChain() {
        final Specification<EntityA> specification = new Specification<EntityA>() {
            @Override
            public Predicate toPredicate(Root<EntityA> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final Traversal ts = Filters.traversal(null, root.getModel(), "b.x.id");
                final Expression<Object> nonExistant = Filters.path("myFilter", root, ts);
                return null;
            }
        };
        repository.findOne(specification, FilterRequest.unfiltered());
    }

    @Test
    public void ensureAcceptsTruePrecondition() {
        Filters.ensure(true, "name", null, "");
    }

    @Test(expected = InvalidFilterRequest.class)
    public void ensureThrowsOnFalsePrecondition() {
        Filters.ensure(false, "name", null, "");
    }

}
