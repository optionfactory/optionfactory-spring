package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.stream.Collectors;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.test.TransactionalPhases;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.filters.Sortable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@TransactionalPhases
public class SortTest {

    @Entity
    @Sortable(name = "byA", path = "a")
    @Sortable(name = "byB", path = "b")
    public static class EntityForSort {

        @Id
        public long id;
        public long a;
        public String b;
    }

    public interface EntityForSortRepository extends JpaRepository<EntityForSort, Long>, WhitelistFilteringRepository<EntityForSort>, JpaSpecificationExecutor<EntityForSort> {
    }

    @Inject
    private EntityForSortRepository repo;

    @BeforeEach
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
        Assertions.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationOrders() {
        final List<EntityForSort> all = repo.findAll(new EvenIdFirst(), FilterRequest.unfiltered());
        final List<String> expected = List.of("B", "D", "F", "A", "C", "E");
        Assertions.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationFiltersAndThenGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final List<EntityForSort> all = repo.findAll(new EvenIdFirst(), FilterRequest.unfiltered(), sort);
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assertions.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationAndThenGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final List<EntityForSort> all = repo.findAll(new EvenIdFirst(), sort);
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assertions.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortWithSpecificationAndThenPagination() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final PageRequest pr = PageRequest.of(0, 10, sort);
        final Page<EntityForSort> all = repo.findAll(new EvenIdFirst(), pr);
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assertions.assertEquals(expected, all.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortPageWithGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final Page<EntityForSort> page = repo.findAll(PageRequest.of(0, Integer.MAX_VALUE, sort));
        final List<String> expected = List.of("D", "C", "B", "F", "E", "A");
        Assertions.assertEquals(expected, page.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    @Test
    public void canSortPageWithSpecificationAndThenGivenOrders() {
        final Sort sort = Sort.by(Sort.Order.asc("byA"), Sort.Order.desc("byB"));
        final Page<EntityForSort> page = repo.findAll(new EvenIdFirst(), FilterRequest.unfiltered(), PageRequest.of(0, Integer.MAX_VALUE, sort));
        final List<String> expected = List.of("D", "B", "F", "C", "E", "A");
        Assertions.assertEquals(expected, page.stream().map(e -> e.b).collect(Collectors.toList()));
    }

    private static class EvenIdFirst implements Specification<EntityForSort> {

        @Override
        public Predicate toPredicate(Root<EntityForSort> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
            query.orderBy(
                    criteriaBuilder.asc(
                            criteriaBuilder.selectCase()
                                    .when(criteriaBuilder.equal(
                                            // Since Hibernate 7.3.0.Final, criteria type checks became stricter.
                                            // CriteriaBuilder.mod() supports only Integer numeric type, making a Long expression an incompatible argument.
                                            // The following criterion is no longer valid with an "id" field of Long/long type:
                                            //     criteriaBuilder.mod(root.get("id").as(Integer.class), 2),
                                            // Therefore, we have to use the CriteriaBuilder.function() method instead, with the "mod" function name.
                                            // It would be nicer to have CriteriaBuilder implementing mod for Long expressions as well.
                                            criteriaBuilder.function("mod", Long.class, root.get("id"), criteriaBuilder.literal(2L)),
                                            0), 0)
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
