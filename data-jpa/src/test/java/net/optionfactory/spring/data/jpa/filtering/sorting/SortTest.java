package net.optionfactory.spring.data.jpa.filtering.sorting;

import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import net.optionfactory.spring.spring.data.jpa.HibernateTestConfig;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = HibernateTestConfig.class)
@Transactional
public class SortTest {

    @Autowired
    private EntityForSortRepository repo;

    @Before
    public void setup() {
        repo.save(entity(1, 2, "A"));
        repo.save(entity(2, 1, "B"));
        repo.save(entity(3, 1, "C"));
        repo.save(entity(4, 1, "D"));
        repo.save(entity(5, 2, "E"));
        repo.save(entity(6, 2, "F"));
    }

    @Test
    public void canSortWithGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final List<EntityForSort> all = repo.findAll(sort);
        final List<String> expected = List.of("D", "C", "B", "F", "E", "A");
        Assert.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationOrders() {
        final List<EntityForSort> all = repo.findAll(new EvenIdFirst(), FilterRequest.unfiltered());
        final List<String> expected = List.of("B", "D", "F", "A", "C", "E");
        Assert.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationFiltersAndThenGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final List<EntityForSort> all = repo.findAll(new EvenIdFirst(), FilterRequest.unfiltered(), sort);
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assert.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationAndThenGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final List<EntityForSort> all = repo.findAll(new EvenIdFirst(), sort);
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assert.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationAndThenPagination() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final PageRequest pr = PageRequest.of(0, 10, sort);
        final Page<EntityForSort> all = repo.findAll(new EvenIdFirst(), pr);
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assert.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortPageWithGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final Page<EntityForSort> page = repo.findAll(PageRequest.of(0, Integer.MAX_VALUE, sort));
        final List<String> expected = List.of("D", "C", "B", "F", "E", "A");
        Assert.assertEquals(expected, page.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortPageWithSpecificationAndThenGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final Page<EntityForSort> page = repo.findAll(new EvenIdFirst(), FilterRequest.unfiltered(), PageRequest.of(0, Integer.MAX_VALUE, sort));
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assert.assertEquals(expected, page.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    private static class EvenIdFirst implements Specification<EntityForSort> {

        @Override
        public Predicate toPredicate(Root<EntityForSort> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
            query.orderBy(
                    criteriaBuilder.asc(
                            criteriaBuilder.selectCase()
                                    .when(criteriaBuilder.equal(criteriaBuilder.mod(root.get("id"), 2), 0), 0)
                                    .otherwise(1)
                    )
            );
            return null;
        }
    }

    private static EntityForSort entity(long id, long a, String b) {
        final EntityForSort entity = new EntityForSort();
        entity.id = id;
        entity.a = a;
        entity.b = b;
        return entity;
    }
}
