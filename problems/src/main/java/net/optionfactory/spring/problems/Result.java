package net.optionfactory.spring.problems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Result of a computation. Can be _either_ a value or an error.
 *
 * @author rferranti
 * @param <V> the value type
 */
public sealed interface Result<V> permits Result.Ok, Result.Err {

    static <V> Result<V> ok(V value) {
        return new Ok<>(value);
    }

    static <V> Result<V> err(List<Problem> errors) {
        return new Err<>(errors);
    }

    static <V> Result<V> err(Problem... errors) {
        return new Err<>(List.of(errors));
    }

    V unwrap();

    V unwrapOr(V defaultValue);

    V unwrapOrElse(Supplier<? extends V> fallbackSupplier);

    <R> Result<R> map(Function<? super V, ? extends R> mapper);

    Result<V> mapErr(Function<? super List<Problem>, ? extends List<Problem>> mapper);

    <R> Result<R> flatMap(Function<? super V, ? extends Result<R>> mapper);

    Result<V> inspect(Consumer<? super V> consumer);

    Result<V> inspectErr(Consumer<? super List<Problem>> consumer);

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

    record Ok<V>(V value) implements Result<V> {

        @Override
        public V unwrap() {
            return value;
        }

        @Override
        public V unwrapOr(V defaultValue) {
            return value;
        }

        @Override
        public V unwrapOrElse(Supplier<? extends V> fallbackSupplier) {
            return value;
        }

        @Override
        public <R> Result<R> map(Function<? super V, ? extends R> mapper) {
            return new Ok<>(mapper.apply(value));
        }

        @Override
        public Result<V> mapErr(Function<? super List<Problem>, ? extends List<Problem>> mapper) {
            return this;
        }

        @Override
        public <R> Result<R> flatMap(Function<? super V, ? extends Result<R>> mapper) {
            return mapper.apply(value);
        }

        @Override
        public Result<V> inspect(Consumer<? super V> consumer) {
            consumer.accept(value);
            return this;
        }

        @Override
        public Result<V> inspectErr(Consumer<? super List<Problem>> consumer) {
            return this;
        }
    }

    record Err<V>(List<Problem> errors) implements Result<V> {

        @SuppressWarnings("unchecked")
        public <R> Result<R> propagate() {
            return (Result<R>) this;
        }

        @Override
        public V unwrap() {
            throw Failure.of(errors);
        }

        @Override
        public V unwrapOr(V defaultValue) {
            return defaultValue;
        }

        @Override
        public V unwrapOrElse(Supplier<? extends V> fallbackSupplier) {
            return fallbackSupplier.get();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> map(Function<? super V, ? extends R> mapper) {
            return (Result<R>) this;
        }

        @Override
        public Result<V> mapErr(Function<? super List<Problem>, ? extends List<Problem>> mapper) {
            return new Err<>(List.copyOf(mapper.apply(errors)));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> flatMap(Function<? super V, ? extends Result<R>> mapper) {
            return (Result<R>) this;
        }

        @Override
        public Result<V> inspect(Consumer<? super V> consumer) {
            return this;
        }

        @Override
        public Result<V> inspectErr(Consumer<? super List<Problem>> consumer) {
            consumer.accept(errors);
            return this;
        }
    }
}