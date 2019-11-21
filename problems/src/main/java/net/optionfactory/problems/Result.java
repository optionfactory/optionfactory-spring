package net.optionfactory.problems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Result of a computation.Can be _either_ a value or an error.
 *
 * @author rferranti
 * @param <V>
 */
public class Result<V> {

    private final List<Problem> errors;
    private final V value;
    private final boolean isError;

    public Result(List<Problem> error, V value, boolean isError) {
        this.errors = error;
        this.value = value;
        this.isError = isError;
    }

    public static <ValueType> Result<ValueType> errors(List<Problem> error) {
        return new Result<>(error, null, true);
    }

    public static <ValueType> Result<ValueType> error(Problem error) {
        final List<Problem> errors = new ArrayList<>();
        errors.add(error);
        return new Result<>(errors, null, true);
    }

    public static <ValueType> Result<ValueType> value(ValueType result) {
        return new Result<>(new ArrayList<>(), result, false);
    }

    public List<Problem> getErrors() {
        return errors;
    }

    public boolean isError() {
        return isError;
    }

    public V getValue() {
        return value;
    }

    public <R> Result<R> map(Function<V, R> mapper) {
        if (isError) {
            return Result.errors(errors);
        }
        return Result.value(mapper.apply(value));
    }

    public <R> Result<R> mapErrors() {
        if (!isError) {
            throw new IllegalStateException("cannot call mapErrors on a valued result");
        }
        return Result.errors(errors);
    }

    public static List<Problem> problems(Result<?> first, Result<?>... others) {
        return Stream.concat(Stream.of(first), Stream.of(others))
                .flatMap(r -> r.getErrors().stream())
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "Result{" + "errors=" + errors + ", value=" + value + ", isError=" + isError + '}';
    }

}
