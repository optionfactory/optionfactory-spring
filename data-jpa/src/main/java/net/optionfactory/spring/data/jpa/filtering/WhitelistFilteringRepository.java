package net.optionfactory.spring.data.jpa.filtering;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

/**
 * Add methods for searching and counting using filtering, sorting and
 * pagination.
 *
 * @param <T> the entity type
 */
public interface WhitelistFilteringRepository<T> {

    /**
     * Finds the single entry accepted by the given filters and base
     * {@link Specification}, if any.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the found entry, if present
     */
    Optional<T> findOne(@Nullable Specification<T> base, FilterRequest filters);

    /**
     * Finds a page of entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param pageable the requested page
     * @return a page of found entries
     */
    Page<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Pageable pageable);

    /**
     * Finds all entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the found entries
     */
    List<T> findAll(@Nullable Specification<T> base, FilterRequest filters);

    /**
     * Finds all entries accepted by the given filters and base
     * {@link Specification}, ordered by a {@link Sort}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param sort the order of property values
     * @return the found entries, sorted
     */
    List<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort);

    /**
     * Streams all entries accepted by the given filters and base
     * {@link Specification}, ordered by a {@link Sort}, using the passed
     * {@link StreamingMode}
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param sort the order of property values
     * @param mode the streaming mode
     * @return the found entries, sorted
     */
    Stream<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, StreamingMode mode);

    /**
     * Streams all entries accepted by the given filters and base
     * {@link Specification}, ordered by a {@link Sort}, using the passed
     * {@link StreamingMode}, calling the passed Function before detaching the
     * streamed entity.
     *
     * @param <R> result type
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param sort the order of property values
     * @param mode the streaming mode
     * @param beforeDetaching the mapper to be called before detaching the
     * streamed entity
     * @return the found entries, sorted
     */
    <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, StreamingMode mode, Function<T, R> beforeDetaching);

    /**
     * Counts all entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the total number of entries accepted by the applied filters
     */
    long count(@Nullable Specification<T> base, FilterRequest filters);

    public enum StreamingMode {
        DETACHED, NORMAL;
    }
}
