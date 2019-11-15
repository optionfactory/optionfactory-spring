package net.optionfactory.data.jpa.filtering;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public interface WhitelistFilteringRepository<T> {

    Optional<T> findOne(FilterRequest filters);

    Optional<T> findOne(Specification<T> base, FilterRequest filters);

    List<T> findAll(FilterRequest filters);

    List<T> findAll(Specification<T> base, FilterRequest filters);

    Page<T> findAll(FilterRequest filters, Pageable pageable);

    Page<T> findAll(Specification<T> base, FilterRequest filters, Pageable pageable);

    List<T> findAll(FilterRequest filters, Sort sort);

    List<T> findAll(Specification<T> base, FilterRequest filters, Sort sort);

    long count(FilterRequest filters);

    long count(Specification<T> base, FilterRequest filters);

}
