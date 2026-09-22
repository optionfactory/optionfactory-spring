package net.optionfactory.spring.data.jpa.filtering;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
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
     * Finds the single entry accepted by the given filters and base
     * {@link Specification}, if any.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the found entry, if present
     */
    Optional<T> findOne(@Nullable Specification<T> base, FilterRequest filters);

    Optional<T> findOne(FilterRequest filters);

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

    Page<T> findAll(FilterRequest filters, Pageable pageable);

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
     * Finds all entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the found entries
     */
    List<T> findAll(@Nullable Specification<T> base, FilterRequest filters);

    List<T> findAll(FilterRequest filters);

    List<T> findAll(FilterRequest filters, Sort sort);

    /**
     * Streams all entries accepted by the given filters and base
     * {@link Specification}, ordered by a {@link Sort}, calling the passed
     * mapper and detaching each entity right after it is mapped. Entities are
     * loaded in {@link SessionPolicy.Mode#READ_ONLY read-only mode}: no
     * dirty-check snapshot is kept, so persistence-context memory per entity is
     * roughly halved and the stream bounds its own memory without any
     * {@link SessionPolicy} usage. The mapper must not retain the entity for
     * lazy access after returning: the entity is detached as soon as the
     * mapper's result is produced.
     *
     * @param <R> result type
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param sort the order of property values
     * @param fetchSize the fetch size to be hinted
     * @param mapper the transformation applied to each entity
     * @return the found entries, mapped and sorted
     */
    <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, int fetchSize, Function<T, R> mapper);

    <R> Stream<R> findAll(FilterRequest filters, Sort sort, int fetchSize, Function<T, R> mapper);

    /**
     * Streams all entries accepted by the given filters and base
     * {@link Specification}, ordered by a {@link Sort}, calling the passed
     * Function before possibly detaching the streamed entity, loading
     * entities according to the given {@link SessionPolicy.Mode}.
     *
     * @param <R> result type
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @param sort the order of property values
     * @param fetchSize the fetch size to be hinted
     * @param mode how entities are loaded
     * @param beforeDetaching the mapper to be called before possibly
     * detaching the streamed entity
     * @return the found entries, sorted
     */
    <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, int fetchSize, SessionPolicy.Mode mode, BiFunction<SessionPolicy, T, R> beforeDetaching);

    <R> Stream<R> findAll(FilterRequest filters, Sort sort, int fetchSize, SessionPolicy.Mode mode, BiFunction<SessionPolicy, T, R> beforeDetaching);

    /**
     * Counts all entries accepted by the given filters and base
     * {@link Specification}.
     *
     * @param base a base filter that should be always applied
     * @param filters filters parameters
     * @return the total number of entries accepted by the applied filters
     */
    long count(@Nullable Specification<T> base, FilterRequest filters);

    long count(FilterRequest filters);

    public static class SessionPolicy {

        /**
         * How a streaming query loads its entities.
         */
        public enum Mode {

            /**
             * Entities are managed with a dirty-check snapshot: the callback
             * may mutate them and rely on flush. The mode to use whenever the
             * stream is not a pure read.
             */
            DEFAULT,
            /**
             * Entities are loaded through Hibernate's per-query read-only
             * hint: no dirty-check snapshot is kept, roughly halving
             * persistence-context memory per entity, and mutations made by
             * the callback are silently ignored at flush. The hint affects
             * only the entities this query loads, never the rest of the
             * caller's transaction. Entities still accumulate in the
             * persistence context as the stream advances: the
             * {@link Function}-based overloads detach each entity right
             * after mapping, while policy-based callbacks should evict with
             * {@link SessionPolicy#detaching} per row or bulk
             * {@link SessionPolicy#clear}/{@link SessionPolicy#clearIf} (the
             * cheaper option on large scans).
             */
            READ_ONLY;
        }

        private final EntityManager em;
        private final AtomicLong counter;

        public SessionPolicy(EntityManager em, AtomicLong counter) {
            this.em = em;
            this.counter = counter;
        }

        public <T> T detaching(T entity) {
            em.detach(entity);
            return entity;
        }

        public void clear() {
            em.clear();
        }

        public long current() {
            return counter.get();
        }

        public void clearIf(int mod) {
            if (counter.get() % mod != 0) {
                return;
            }
            em.clear();
        }

    }

}
