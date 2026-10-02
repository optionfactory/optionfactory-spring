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

/// Repository methods searching, counting and streaming entities by the filters and sorters
/// whitelisted on the entity, as requested by a client.
///
/// A repository interface extends it next to a spring data repository, and is enabled by
/// [EnableJpaWhitelistFilteringRepositories], whose base class implements these methods. The
/// whitelist is the set of filter annotations (`@TextCompare`, `@InEnum`, ..., `@Filterable`) and
/// `@Sortable` annotations on the entity, read once when the repository is created: a misconfigured
/// one fails the startup with an
/// [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterConfiguration] or an
/// [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortConfiguration].
///
/// ```java
/// @Entity
/// @TextCompare(name = "byName", path = "name")
/// @InEnum(name = "byType", path = "type", type = PetType.class)
/// @Sortable(name = "byName", path = "name")
/// public class Pet { ... }
///
/// public interface PetRepository extends JpaRepository<Pet, Long>, WhitelistFilteringRepository<Pet> {
/// }
///
/// final Page<Pet> page = pets.findAll(request, PageRequest.of(0, 20, Sort.by("byName")));
/// ```
///
/// Requested filters are combined in `AND`, together with the `base` [Specification] when one is
/// given: `base` is where the application adds the restrictions the client does not choose, such as
/// tenancy or visibility, and may be `null`. Sort properties, including those of a [Pageable], are
/// the names of whitelisted sorters, never entity properties. The base class applies this to the
/// inherited spring data methods taking a [Sort] or a [Pageable] as well.
///
/// A filter or sorter name that is not whitelisted, or values a filter cannot accept, are
/// rejected with an [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest]
/// or an [net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest]. Both are
/// `IllegalArgumentException`s, which spring's persistence exception translation wraps in an
/// `InvalidDataAccessApiUsageException` when they cross the repository proxy: the rejection is its
/// cause.
///
/// @param <T> the entity type
public interface WhitelistFilteringRepository<T> {

    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @return the single entity matching both, or empty when none does
    /// @throws org.springframework.dao.IncorrectResultSizeDataAccessException when more than one
    /// entity matches
    Optional<T> findOne(@Nullable Specification<T> base, FilterRequest filters);

    /// Same as [#findOne(Specification, FilterRequest)] with no base restriction.
    ///
    /// @param filters the requested filters
    /// @return the single matching entity, or empty when none does
    Optional<T> findOne(FilterRequest filters);

    /// The total of the returned page is counted with the same restrictions, by a separate count
    /// query that the sort does not affect. An unpaged [Pageable] returns every match, with no
    /// count query.
    ///
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param pageable the requested page, sorted by whitelisted sorter names
    /// @return the requested page of the matching entities
    Page<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Pageable pageable);

    /// Same as [#findAll(Specification, FilterRequest, Pageable)] with no base restriction.
    ///
    /// @param filters the requested filters
    /// @param pageable the requested page, sorted by whitelisted sorter names
    /// @return the requested page of the matching entities
    Page<T> findAll(FilterRequest filters, Pageable pageable);

    /// Orders defined by the `base` specification on the query come first, followed by the
    /// requested ones.
    ///
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @return every matching entity, sorted
    List<T> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort);

    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @return every matching entity, in no particular order
    List<T> findAll(@Nullable Specification<T> base, FilterRequest filters);

    /// @param filters the requested filters
    /// @return every matching entity, in no particular order
    List<T> findAll(FilterRequest filters);

    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @return every matching entity, sorted
    List<T> findAll(FilterRequest filters, Sort sort);

    /// Streams the matching entities through `mapper`, detaching each entity right after it is
    /// mapped.
    ///
    /// Entities are loaded in [SessionPolicy.Mode#READ_ONLY] mode: no dirty-check snapshot is
    /// kept, so the persistence-context memory per entity is roughly halved, and with each entity
    /// detached the stream bounds its own memory without any [SessionPolicy] usage. The mapper must
    /// not retain the entity for lazy access after returning: it is detached as soon as the
    /// mapper's result is produced.
    ///
    /// The stream borrows the caller's transaction and the underlying JDBC scroll: consume it
    /// while the transaction is active, and close it (try-with-resources) when done. Both halves of
    /// the contract are on the caller; consuming past the transaction end fails against a closed
    /// session, and abandoning the stream unclosed holds JDBC resources until the session ends.
    ///
    /// ```java
    /// try (final var names = pets.findAll(request, Sort.by("byName"), 500, pet -> pet.name)) {
    ///     names.forEach(writer::println);
    /// }
    /// ```
    ///
    /// @param <R> the result type
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mapper the transformation applied to each entity
    /// @return the mapped entities, sorted
    <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, int fetchSize, Function<T, R> mapper);

    /// Same as [#findAll(Specification, FilterRequest, Sort, int, Function)] with no base
    /// restriction.
    ///
    /// @param <R> the result type
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mapper the transformation applied to each entity
    /// @return the mapped entities, sorted
    <R> Stream<R> findAll(FilterRequest filters, Sort sort, int fetchSize, Function<T, R> mapper);

    /// Streams the matching entities through `beforeDetaching`, loading them according to `mode`
    /// and leaving to the callback, through the [SessionPolicy] it receives, whether and when to
    /// detach them or clear the persistence context.
    ///
    /// The stream borrows the caller's transaction and the underlying JDBC scroll: consume it
    /// while the transaction is active, and close it (try-with-resources) when done. Both halves of
    /// the contract are on the caller; consuming past the transaction end fails against a closed
    /// session, and abandoning the stream unclosed holds JDBC resources until the session ends.
    ///
    /// The row counter behind [SessionPolicy#clearIf] and [SessionPolicy#current] is incremented
    /// before the callback is invoked, so the callback sees the 1-based number of the row it is
    /// handling. It counts mapped rows and assumes the stream is consumed linearly.
    ///
    /// ```java
    /// try (final var ids = pets.findAll(request, Sort.by("byName"), 500, SessionPolicy.Mode.DEFAULT, (policy, pet) -> {
    ///     pet.reviewed = true;
    ///     policy.clearIf(500);
    ///     return pet.id;
    /// })) {
    ///     ids.forEach(audit::reviewed);
    /// }
    /// ```
    ///
    /// Clearing discards unflushed changes: a callback mutating entities in [SessionPolicy.Mode#DEFAULT]
    /// mode should flush before clearing.
    ///
    /// @param <R> the result type
    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mode how entities are loaded
    /// @param beforeDetaching the transformation applied to each entity, given the policy
    /// controlling the persistence context
    /// @return the mapped entities, sorted
    <R> Stream<R> findAll(@Nullable Specification<T> base, FilterRequest filters, Sort sort, int fetchSize, SessionPolicy.Mode mode, BiFunction<SessionPolicy, T, R> beforeDetaching);

    /// Same as [#findAll(Specification, FilterRequest, Sort, int, SessionPolicy.Mode, BiFunction)]
    /// with no base restriction.
    ///
    /// @param <R> the result type
    /// @param filters the requested filters
    /// @param sort the requested order, by whitelisted sorter names
    /// @param fetchSize the JDBC fetch size hinted to the driver
    /// @param mode how entities are loaded
    /// @param beforeDetaching the transformation applied to each entity, given the policy
    /// controlling the persistence context
    /// @return the mapped entities, sorted
    <R> Stream<R> findAll(FilterRequest filters, Sort sort, int fetchSize, SessionPolicy.Mode mode, BiFunction<SessionPolicy, T, R> beforeDetaching);

    /// @param base a restriction always applied, or `null` for none
    /// @param filters the requested filters
    /// @return the number of entities matching both
    long count(@Nullable Specification<T> base, FilterRequest filters);

    /// @param filters the requested filters
    /// @return the number of matching entities
    long count(FilterRequest filters);

    /// The handle a streaming callback receives to manage the persistence context the streamed
    /// entities accumulate in.
    ///
    /// One instance serves one stream, and is not meant to be used outside the callback or from
    /// another thread.
    public static class SessionPolicy {

        /// How a streaming query loads its entities.
        public enum Mode {

            /// Entities are managed with a dirty-check snapshot: the callback may mutate them and
            /// rely on flush. The mode to use whenever the stream is not a pure read.
            DEFAULT,
            /// Entities are loaded through Hibernate's per-query read-only hint: no dirty-check
            /// snapshot is kept, roughly halving persistence-context memory per entity, and
            /// mutations made by the callback are silently ignored at flush. The hint affects only
            /// the entities this query loads, never the rest of the caller's transaction. Entities
            /// still accumulate in the persistence context as the stream advances: the
            /// [Function]-based overloads detach each entity right after mapping, while
            /// policy-based callbacks should evict with [SessionPolicy#detaching] per row or bulk
            /// [SessionPolicy#clear]/[SessionPolicy#clearIf] (the cheaper option on large scans).
            READ_ONLY;
        }

        private final EntityManager em;
        private final AtomicLong counter;

        /// Created by the repository for each stream.
        ///
        /// @param em the entity manager the stream loads its entities in
        /// @param counter the row counter, incremented by the stream before each callback
        public SessionPolicy(EntityManager em, AtomicLong counter) {
            this.em = em;
            this.counter = counter;
        }

        /// Detaches an entity from the persistence context, discarding its unflushed changes.
        ///
        /// @param <T> the entity type
        /// @param entity the entity to detach
        /// @return the same entity, now detached, so that a callback can end with
        /// `return policy.detaching(entity);`
        public <T> T detaching(T entity) {
            em.detach(entity);
            return entity;
        }

        /// Clears the whole persistence context, detaching every managed entity (not only the
        /// streamed ones) and discarding their unflushed changes.
        public void clear() {
            em.clear();
        }

        /// Current 1-based row number, as seen by the streaming callback. Counts mapped rows: a
        /// stream consumed non-linearly (skip, limit, takeWhile) does not observe every row.
        ///
        /// @return the number of rows mapped so far, the current one included
        public long current() {
            return counter.get();
        }

        /// Clears the persistence context, as [#clear()] does, whenever the current row number is
        /// a multiple of `mod`.
        ///
        /// @param mod the bulk-clear cadence, in rows
        /// @throws ArithmeticException when `mod` is zero
        public void clearIf(int mod) {
            if (counter.get() % mod != 0) {
                return;
            }
            em.clear();
        }

    }

}
