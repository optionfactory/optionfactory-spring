package net.optionfactory.spring.data.jpa.filtering.h2.reduction;

import jakarta.persistence.EntityManager;
import net.optionfactory.spring.data.jpa.filtering.Filter;
import net.optionfactory.spring.data.jpa.filtering.FilterRequest;
import net.optionfactory.spring.data.jpa.filtering.WhitelistFilteringSpecificationAdapter;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.Repositories;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;

import java.util.Map;

public class ReductionNumberEntityRepositoryImpl implements ReductionNumberEntityRepository {

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
        final var predicate = new WhitelistFilteringSpecificationAdapter<NumberEntity>(request, this.allowedFilters).toPredicate(root, query, entityManager.getCriteriaBuilder());

        query.where(predicate);

        //The multiselect parameters must be in the same order as the Reduction constructor
        final var select = query.multiselect(
                builder.count(root),
                builder.min(root.get("number")),
                builder.max(root.get("number")),
                builder.avg(root.get("number"))
        );
        return entityManager.createQuery(select).setMaxResults(1).getSingleResult();
    }
}
