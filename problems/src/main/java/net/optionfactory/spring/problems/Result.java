package net.optionfactory.spring.problems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Result of a computation.Can be _either_ a value or an error.
 *
 * @author rferranti
 * @param <V>
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

    boolean isError();

    V value();

    List<Problem> errors();

    V unwrap();

    <R> Result<R> map(Function<V, R> mapper);

    <R> Result<R> propagate();

    static List<Problem> collectProblems(Result<?> first, Result<?>... others) {
        final var all = new ArrayList<>(first.errors());
        for (var r : others) {
            all.addAll(r.errors());
        }
        return all;
    }

    record Ok<V>(V value) implements Result<V> {

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public List<Problem> errors() {
            return List.of();
        }

        @Override
        public V value() {
            return value;
        }

        @Override
        public V unwrap() {
            return value;
        }

        @Override
        public <R> Result<R> map(Function<V, R> mapper) {
            return new Ok<>(mapper.apply(value));
        }

        @Override
        public <R> Result<R> propagate() {
            throw new IllegalStateException("cannot propagate on a valued result");
        }
    }

    record Err<V>(List<Problem> errors) implements Result<V> {

        @Override
        public boolean isError() {
            return true;
        }

        @Override
        public V value() {
            return null;
        }

        @Override
        public List<Problem> errors() {
            return errors;
        }

        @Override
        public V unwrap() {
            throw new IllegalStateException("Result is an error");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> map(Function<V, R> mapper) {
            return (Result<R>) this;
        }

        @Override
        public <R> Result<R> propagate() {
            return new Err<>(errors);
        }
    }
}
