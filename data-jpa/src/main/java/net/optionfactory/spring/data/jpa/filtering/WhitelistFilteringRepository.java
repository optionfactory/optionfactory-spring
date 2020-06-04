package net.optionfactory.spring.data.jpa.filtering;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Add methods for searching and counting using filtering, sorting and
 * pagination.
 *
 * @param <T> the entity type
 */
public interface WhitelistFilteringRepository<T> {

    /**
     * Finds the single entry accepted by the given filters, if any.
     *
     * @param filters filters parameters
     * @return the found entry, if present
     */
    Optional<T> findOne(FilterRequest filters);

    /**
     * Finds the single entry accepted by the given filters and base
     * {@link Specification}, if any.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the found entry, if present
     */
    Optional<T> findOne(Specification<T> base, FilterRequest filters);

    /**
     * Finds all entries accepted by the given filters.
     *
     * @param filters filters parameters
     * @return the found entries
     */
    List<T> findAll(FilterRequest filters);

    /**
     * Finds all entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the found entries
     */
    List<T> findAll(Specification<T> base, FilterRequest filters);

    /**
     * Finds a page of entries accepted by the given filters.
     *
     * @param filters filters parameters
     * @param pageable the requested page
     * @return a page of found entries
     */
    Page<T> findAll(FilterRequest filters, Pageable pageable);

    /**
     * Finds a page of entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param pageable the requested page
     * @return a page of found entries
     */
    Page<T> findAll(Specification<T> base, FilterRequest filters, Pageable pageable);

    /**
     * Finds all entries accepted by the given filters, ordered by a
     * {@link Sort}.
     *
     * @param filters filters parameters
     * @param sort the order of property values
     * @return the found entries, sorted
     */
    List<T> findAll(FilterRequest filters, Sort sort);

    /**
     * Finds all entries accepted by the given filters and base
     * {@link Specification}, ordered by a {@link Sort}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param sort the order of property values
     * @return the found entries, sorted
     */
    List<T> findAll(Specification<T> base, FilterRequest filters, Sort sort);

    /**
     * Counts all entries accepted by the given filters.
     *
     * @param filters filters parameters
     * @return the total number of entries accepted by the applied filters
     */
    long count(FilterRequest filters);

    /**
     * Counts all entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the total number of entries accepted by the applied filters
     */
    long count(Specification<T> base, FilterRequest filters);
}
