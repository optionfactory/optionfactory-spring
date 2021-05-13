package net.optionfactory.spring.data.jpa.filtering.filters.spi;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Filters.Traversal;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateTestConfig.class)
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
                final Path<?> path = Filters.path(root, ts);
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
                final Path<Object> path = Filters.path(root, ts);
                Assert.assertEquals(long.class, path.getJavaType());
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
                final Path<Object> nonExistant = Filters.path(root, ts);
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
