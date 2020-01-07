package net.optionfactory.data.jpa.filtering.filters.spi;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import net.optionfactory.data.jpa.HibernateTestConfig;
import net.optionfactory.data.jpa.filtering.FilterRequest;
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
    public void canTraversePropertyChain() {
        final Specification<EntityA> specification = new Specification<EntityA>() {
            @Override
            public Predicate toPredicate(Root<EntityA> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final Path<Object> path = Filters.traverseProperty(root, "b.c.i.n");
                Assert.assertEquals(long.class, path.getJavaType());
                return cb.disjunction();
            }
        };
        repository.findOne(specification, FilterRequest.unfiltered());
    }

    @Test(expected = InvalidFilterConfiguration.class)
    public void throwsWhenNonExistantPropertyIsReferencedInPropertyChain() {
        final Specification<EntityA> specification = new Specification<EntityA>() {
            @Override
            public Predicate toPredicate(Root<EntityA> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
                final Path<Object> nonExistant = Filters.traverseProperty(root, "b.x.id");
                return cb.disjunction();
            }
        };
        repository.findOne(specification, FilterRequest.unfiltered());
    }

    @Test
    public void ensureAcceptsTruePrecondition() {
        Filters.ensure(true, "");
    }

    @Test(expected = InvalidFilterRequest.class)
    public void ensureThrowsOnFalsePrecondition() {
        Filters.ensure(false, "");
    }
}
