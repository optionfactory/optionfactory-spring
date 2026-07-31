package net.optionfactory.spring.data.jpa.filtering.h2;

import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import java.util.Arrays;
import java.util.Map;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.h2.HibernateOnH2TestConfig;
import net.optionfactory.spring.data.jpa.filtering.PerMethodTransactional;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringRepository;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringSpecificationAdapter;
import net.optionfactory.spring.data.jpa.filtering.filters.NumberCompare;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(HibernateOnH2TestConfig.class)
@PerMethodTransactional
public class ReductionTest {

    @Entity
    @NumberCompare(name = "number", path = "number")
    public static class NumberEntity {

        @Id
        public long id;
        public long number;
    }

    public interface NumberEntityRepository extends JpaRepository<NumberEntity, Long>, WhitelistFilteringRepository<NumberEntity>, ReductionNumberEntityRepository {

    }

    public interface ReductionNumberEntityRepository {

        Reduction reduce(FilterRequest request);

        public record Reduction(long count, long min, long max, double average) {

        }
    }

    public static class ReductionNumberEntityRepositoryImpl implements ReductionNumberEntityRepository {

        private final EntityManager entityManager;
        private final Map<String, Filter> allowedFilters;
        private final Map<String, String> allowedSorters;

        public ReductionNumberEntityRepositoryImpl(EntityManager em) {
            final var ei = JpaEntityInformationSupport.getEntityInformation(NumberEntity.class, em);
            this.entityManager = em;
            this.allowedFilters = Repositories.allowedFilters(ei, em);
            this.allowedSorters = Repositories.allowedSorters(ei, em);
        }

        @Override
        public Reduction reduce(FilterRequest request) {
            final var builder = entityManager.getCriteriaBuilder();
            final var query = builder.createQuery(Reduction.class);
            final var root = query.from(NumberEntity.class);
            final var predicate = new WhitelistFilteringSpecificationAdapter<NumberEntity>(request, this.allowedFilters).toPredicate(root, query, builder);

            final var select = query
                    .where(predicate)
                    .select(
                            builder.construct(Reduction.class,
                                    builder.count(root),
                                    builder.min(root.get("number")),
                                    builder.max(root.get("number")),
                                    builder.avg(root.get("number"))
                            )
                    );
            return entityManager.createQuery(select).setMaxResults(1).getSingleResult();
        }
    }
    @Inject
    public NumberEntityRepository repo;

    @BeforeEach
    public void setup() {
        repo.saveAll(Arrays.asList(
                entity(1, 3),
                entity(2, 15),
                entity(3, 10),
                entity(4, 5)
        ));
    }

    @Test
    public void canPerformReductionWithoutFiltering() {
        final ReductionNumberEntityRepository.Reduction reduced = repo.reduce(FilterRequest.builder().build());
        Assertions.assertEquals(4, reduced.count());
        Assertions.assertEquals(3, reduced.min());
        Assertions.assertEquals(15, reduced.max());
        Assertions.assertEquals(8.25, reduced.average(), 0.0);
    }

    @Test
    public void canPerformReductionWithFiltering() {
        final ReductionNumberEntityRepository.Reduction reduced = repo.reduce(FilterRequest.builder()
                .number("number", filter -> filter.gt(8))
                .build());
        Assertions.assertEquals(2, reduced.count());
        Assertions.assertEquals(10, reduced.min());
        Assertions.assertEquals(15, reduced.max());
        Assertions.assertEquals(12.5, reduced.average(), 0.0);
    }

    private NumberEntity entity(int id, int number) {
        final var e = new NumberEntity();
        e.id = id;
        e.number = number;
        return e;
    }

}
