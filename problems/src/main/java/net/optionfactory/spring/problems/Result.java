package net.optionfactory.spring.problems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/// The result of a computation: _either_ a value ([Ok]) or the [Problem]s that prevented it
/// ([Err]), for code that reports errors by returning them rather than by throwing a [Failure].
///
/// The combinators transform the value or the problems while passing the other side through
/// untouched, and being sealed, a result can also be taken apart with an exhaustive `switch`:
///
/// ```java
/// public Result<Order> order(int quantity) {
///     if (quantity <= 0) {
///         return Result.error(Problem.field("quantity", "must be positive"));
///     }
///     return Result.value(new Order(quantity));
/// }
///
/// final BigDecimal total = order(quantity)
///         .map(Order::total)
///         .inspectErr(problems -> logger.debug("rejected: {}", problems))
///         .unwrapOr(BigDecimal.ZERO);
///
/// final Order placed = switch (order(quantity)) {
///     case Result.Ok<Order>(var o) -> o;
///     case Result.Err<Order>(var problems) -> throw Failure.of(problems);
/// };
/// ```
///
/// Nothing is checked or copied on construction: an `Ok` may hold `null`, and an `Err` holds the
/// list it is given, possibly empty or mutable.
///
/// @author rferranti
/// @param <V> the value type
public sealed interface Result<V> permits Result.Ok, Result.Err {

    /// @param <V> the value type
    /// @param value the value, possibly `null`
    /// @return a successful result, typed as a `Result`
    static <V> Result<V> value(V value) {
        return new Ok<>(value);
    }

    /// @param <V> the value type
    /// @param errors the problems, kept as given
    /// @return a failed result, typed as a `Result`
    static <V> Result<V> error(List<Problem> errors) {
        return new Err<>(errors);
    }

    /// @param <V> the value type
    /// @param errors the problems, none of them `null`
    /// @return a failed result, typed as a `Result`
    static <V> Result<V> error(Problem... errors) {
        return new Err<>(List.of(errors));
    }

    /// Like [#value(Object)], typed as the more specific `Ok`.
    ///
    /// @param <V> the value type
    /// @param value the value, possibly `null`
    /// @return a successful result
    static <V> Ok<V> ok(V value) {
        return new Ok<>(value);
    }

    /// Like [#error(List)], typed as the more specific `Err`.
    ///
    /// @param <V> the value type
    /// @param errors the problems, kept as given
    /// @return a failed result
    static <V> Err<V> err(List<Problem> errors) {
        return new Err<>(errors);
    }

    /// Like [#error(Problem...)], typed as the more specific `Err`.
    ///
    /// @param <V> the value type
    /// @param errors the problems, none of them `null`
    /// @return a failed result
    static <V> Err<V> err(Problem... errors) {
        return new Err<>(List.of(errors));
    }

    /// @return the value of an `Ok`
    /// @throws Failure carrying the problems, without cause or message, on an `Err`
    V unwrap();

    /// @param defaultValue the value to return on an `Err`
    /// @return the value of an `Ok`, `defaultValue` on an `Err`
    V unwrapOr(V defaultValue);

    /// @param fallbackSupplier called only on an `Err`, to compute the value to return
    /// @return the value of an `Ok`, the supplied fallback on an `Err`
    V unwrapOrElse(Supplier<? extends V> fallbackSupplier);

    /// @return the problems of an `Err`
    /// @throws IllegalStateException on an `Ok`
    List<Problem> unwrapErr();

    /// @param <R> the mapped value type
    /// @param mapper applied to the value of an `Ok`, never called on an `Err`
    /// @return an `Ok` holding what the mapper returned, `null` included, or this `Err`, retyped
    <R> Result<R> map(Function<? super V, ? extends R> mapper);

    /// @param mapper applied to the problems of an `Err`, never called on an `Ok`
    /// @return an `Err` holding an unmodifiable copy of what the mapper returned, or this `Ok`
    /// @throws NullPointerException when the mapper returns `null` or a list containing `null`
    Result<V> mapErr(Function<? super List<Problem>, ? extends List<Problem>> mapper);

    /// Chains a computation that can itself fail.
    ///
    /// @param <R> the value type of the chained computation
    /// @param mapper applied to the value of an `Ok`, never called on an `Err`
    /// @return what the mapper returned, or this `Err`, retyped
    <R> Result<R> flatMap(Function<? super V, ? extends Result<R>> mapper);

    /// Runs a side effect, such as logging, on the value.
    ///
    /// @param consumer called with the value of an `Ok`, never called on an `Err`
    /// @return this result
    Result<V> inspect(Consumer<? super V> consumer);

    /// Runs a side effect, such as logging, on the problems.
    ///
    /// @param consumer called with the problems of an `Err`, never called on an `Ok`
    /// @return this result
    Result<V> inspectErr(Consumer<? super List<Problem>> consumer);

    /// Gathers the problems of several independent results, to report every failure at once
    /// rather than the first.
    ///
    /// @param first a result
    /// @param others more results
    /// @return the problems of every `Err`, in argument order, a new mutable list, empty when all
    ///         results are `Ok`
    static List<Problem> collectProblems(Result<?> first, Result<?>... others) {
        final var all = new ArrayList<Problem>();
        if (first instanceof Err<?> err) {
            all.addAll(err.errors());
        }
        for (var r : others) {
            if (r instanceof Err<?> err) {
                all.addAll(err.errors());
            }
        }
        return all;
    }

    /// Gathers the problems of several independent results, to report every failure at once
    /// rather than the first.
    ///
    /// @param <T> the value type
    /// @param results the results
    /// @return the problems of every `Err`, in iteration order, a new mutable list, empty when all
    ///         results are `Ok`
    static <T> List<Problem> collectProblems(Iterable<Result<T>> results) {
        final var all = new ArrayList<Problem>();
        for (var r : results) {
            if (r instanceof Err<?> err) {
                all.addAll(err.errors());
            }
        }
        return all;
    }

    /// A successful result.
    ///
    /// @param <V> the value type
    /// @param value the value, possibly `null`
    record Ok<V>(V value) implements Result<V> {

        /// @return the value
        @Override
        public V unwrap() {
            return value;
        }

        /// @param defaultValue ignored
        /// @return the value
        @Override
        public V unwrapOr(V defaultValue) {
            return value;
        }

        /// @param fallbackSupplier ignored, never called
        /// @return the value
        @Override
        public V unwrapOrElse(Supplier<? extends V> fallbackSupplier) {
            return value;
        }

        /// @return never
        /// @throws IllegalStateException always: a value has no problems
        @Override
        public List<Problem> unwrapErr() {
            throw new IllegalStateException("Result is a value");
        }

        /// @param <R> the mapped value type
        /// @param mapper applied to the value
        /// @return an `Ok` holding what the mapper returned
        @Override
        public <R> Result<R> map(Function<? super V, ? extends R> mapper) {
            return new Ok<>(mapper.apply(value));
        }

        /// @param mapper ignored, never called
        /// @return this
        @Override
        public Result<V> mapErr(Function<? super List<Problem>, ? extends List<Problem>> mapper) {
            return this;
        }

        /// @param <R> the value type of the chained computation
        /// @param mapper applied to the value
        /// @return what the mapper returned
        @Override
        public <R> Result<R> flatMap(Function<? super V, ? extends Result<R>> mapper) {
            return mapper.apply(value);
        }

        /// @param consumer called with the value
        /// @return this
        @Override
        public Result<V> inspect(Consumer<? super V> consumer) {
            consumer.accept(value);
            return this;
        }

        /// @param consumer ignored, never called
        /// @return this
        @Override
        public Result<V> inspectErr(Consumer<? super List<Problem>> consumer) {
            return this;
        }
    }

    /// A failed result.
    ///
    /// @param <V> the value type the computation would have produced
    /// @param errors the problems, as given
    record Err<V>(List<Problem> errors) implements Result<V> {

        /// Returns this failure as the result of a computation of another type: an `Err` holds no
        /// value, so it can be passed up unchanged, as in
        /// `if (r instanceof Result.Err<?> err) { return err.propagate(); }`.
        ///
        /// @param <R> the value type of the result to return
        /// @return this, retyped
        @SuppressWarnings("unchecked")
        public <R> Result<R> propagate() {
            return (Result<R>) this;
        }

        /// @return never
        /// @throws Failure always, carrying the problems, without cause or message
        @Override
        public V unwrap() {
            throw Failure.of(errors);
        }

        /// @param defaultValue the value to return
        /// @return `defaultValue`
        @Override
        public V unwrapOr(V defaultValue) {
            return defaultValue;
        }

        /// @param fallbackSupplier computes the value to return
        /// @return what the supplier returned
        @Override
        public V unwrapOrElse(Supplier<? extends V> fallbackSupplier) {
            return fallbackSupplier.get();
        }

        /// @return the problems, as given
        @Override
        public List<Problem> unwrapErr() {
            return errors;
        }

        /// @param <R> the mapped value type
        /// @param mapper ignored, never called
        /// @return this, retyped
        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> map(Function<? super V, ? extends R> mapper) {
            return (Result<R>) this;
        }

        /// @param mapper applied to the problems
        /// @return an `Err` holding an unmodifiable copy of what the mapper returned
        /// @throws NullPointerException when the mapper returns `null` or a list containing `null`
        @Override
        public Result<V> mapErr(Function<? super List<Problem>, ? extends List<Problem>> mapper) {
            return new Err<>(List.copyOf(mapper.apply(errors)));
        }

        /// @param <R> the value type of the chained computation
        /// @param mapper ignored, never called
        /// @return this, retyped
        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> flatMap(Function<? super V, ? extends Result<R>> mapper) {
            return (Result<R>) this;
        }

        /// @param consumer ignored, never called
        /// @return this
        @Override
        public Result<V> inspect(Consumer<? super V> consumer) {
            return this;
        }

        /// @param consumer called with the problems
        /// @return this
        @Override
        public Result<V> inspectErr(Consumer<? super List<Problem>> consumer) {
            consumer.accept(errors);
            return this;
        }
    }
}
