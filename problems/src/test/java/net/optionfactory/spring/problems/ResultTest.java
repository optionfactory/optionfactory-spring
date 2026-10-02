package net.optionfactory.spring.problems;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResultTest {

    private final Problem p1 = Problem.request("auth-context", "Invalid token");
    private final Problem p2 = Problem.server("database-context", "Connection timed out");

    @Test
    public void testOkLifecycle() {
        final var result = Result.ok("Success");
        if (result instanceof Result.Ok(var value)) {
            Assertions.assertEquals("Success", value, "an Ok must deconstruct to its value");
        } else {
            Assertions.fail("Expected Result.Ok");
        }
        Assertions.assertEquals("Success", result.unwrap(), "unwrapping an Ok must yield its value");
        Assertions.assertEquals("Success", result.unwrapOr("Fallback"), "an Ok must ignore the default value");
        Assertions.assertEquals("Success", result.unwrapOrElse(() -> "Lazy Fallback"), "an Ok must ignore the fallback supplier");
    }

    @Test
    public void testErrLifecycle() {
        final var result = Result.err(p1, p2);

        if (result instanceof Result.Err(var errors)) {
            Assertions.assertEquals(2, errors.size(), "an Err must hold every problem it was given");
            Assertions.assertEquals("Invalid token", errors.get(0).reason, "problems must keep their order and reason");
            Assertions.assertEquals("auth-context", errors.get(0).context, "problems must keep their context");
            Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, errors.get(0).type, "problems must keep their type");
        } else {
            Assertions.fail("Expected Result.Err");
        }

        Assertions.assertThrows(Failure.class, result::unwrap, "unwrapping an Err must throw a Failure");
        Assertions.assertEquals("Fallback", result.unwrapOr("Fallback"), "an Err must yield the default value");
        Assertions.assertEquals("Lazy Fallback", result.unwrapOrElse(() -> "Lazy Fallback"), "an Err must yield the supplied fallback");
    }

    @Test
    public void unwrappingAnErrThrowsAFailureCarryingItsProblems() {
        final var failure = Assertions.assertThrows(Failure.class, () -> Result.err(p1, p2).unwrap(), "unwrapping an Err must throw a Failure");
        Assertions.assertEquals(List.of(p1, p2), failure.problems, "the failure must carry the Err's problems, in order");
        Assertions.assertNull(failure.getCause(), "the failure must have no cause");
    }

    @Test
    public void theProblemsOfAnOkCannotBeUnwrapped() {
        Assertions.assertThrows(IllegalStateException.class, () -> Result.ok("value").unwrapErr(), "an Ok has no problems to unwrap");
    }

    @Test
    public void theFallbackSupplierIsNotCalledOnAnOk() {
        final var called = new AtomicBoolean(false);
        Result.ok("value").unwrapOrElse(() -> {
            called.set(true);
            return "fallback";
        });
        Assertions.assertFalse(called.get(), "the fallback must be computed lazily, only for an Err");
    }

    @Test
    public void testMap() {
        final var okMapped = Result.ok("123").map(Integer::parseInt);
        Assertions.assertEquals(Integer.valueOf(123), okMapped.unwrap(), "mapping an Ok must apply the mapper to its value");
        final var err = Result.<String>err(p1);
        final var errMapped = err.map(Integer::parseInt);

        Assertions.assertInstanceOf(Result.Err.class, errMapped, "mapping an Err must yield an Err");
        final var narrowed = (Result.Err<Integer>) errMapped;
        Assertions.assertEquals(1, narrowed.errors().size(), "mapping an Err must keep its problems");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, narrowed.errors().get(0).type, "mapping an Err must keep its problems unchanged");
    }

    @Test
    public void mappingAnOkToNullYieldsAnOkHoldingNull() {
        final var mapped = Result.ok("value").map(v -> null);
        Assertions.assertInstanceOf(Result.Ok.class, mapped, "a mapper returning null must not turn an Ok into an Err");
        Assertions.assertNull(mapped.unwrap(), "the Ok must hold the null the mapper returned");
    }

    @Test
    public void testFlatMap() {
        final var chainSuccess = Result.ok("User123")
                .flatMap(name -> Result.ok(name.length()));
        Assertions.assertEquals(Integer.valueOf(7), chainSuccess.unwrap(), "flat mapping an Ok to an Ok must yield the chained value");

        final var chainFailure = Result.ok("User123")
                .flatMap(name -> Result.err(p1));
        Assertions.assertInstanceOf(Result.Err.class, chainFailure, "flat mapping an Ok to an Err must yield the Err");

        final var err = Result.<String>err(p1);
        final var bypassed = err.flatMap(name -> Result.ok(name.length()));
        Assertions.assertInstanceOf(Result.Err.class, bypassed, "flat mapping an Err must skip the mapper and yield the Err");
    }

    @Test
    public void testMapErr() {
        final var err = Result.err(p1);
        final var transformedErr = err.mapErr(errors -> List.of(p2));

        if (transformedErr instanceof Result.Err(var errors)) {
            Assertions.assertEquals(1, errors.size(), "the mapped Err must hold what the mapper returned");
            Assertions.assertEquals("Connection timed out", errors.get(0).reason, "the mapped Err must hold the mapper's problems");
            Assertions.assertEquals(Problem.TYPE_SERVER_ERROR, errors.get(0).type, "the mapped Err must hold the mapper's problems");
        } else {
            Assertions.fail("Expected Result.Err");
        }

        final var ok = Result.ok("Safe");
        final var bypassed = ok.mapErr(errors -> List.of(p2));
        Assertions.assertEquals("Safe", bypassed.unwrap(), "mapping the problems of an Ok must leave it unchanged");
    }

    @Test
    public void mappingTheProblemsOfAnErrCopiesWhatTheMapperReturned() {
        final var mutable = new ArrayList<Problem>(List.of(p2));
        final var mapped = Result.err(p1).mapErr(errors -> mutable);
        mutable.add(p1);
        Assertions.assertEquals(List.of(p2), mapped.unwrapErr(), "later changes to the mapper's list must not reach the mapped Err");
    }

    @Test
    public void testInspectAndInspectErr() {
        final var okValue = new AtomicReference<String>();
        final var errTriggered = new AtomicBoolean(false);

        Result.ok("Data")
                .inspect(okValue::set)
                .inspectErr(errors -> errTriggered.set(true));

        Assertions.assertEquals("Data", okValue.get(), "inspect must see the value of an Ok");
        Assertions.assertFalse(errTriggered.get(), "inspectErr must not be called on an Ok");

        okValue.set(null);
        Result.<String>err(p1)
                .inspect(okValue::set)
                .inspectErr(errors -> errTriggered.set(true));

        Assertions.assertNull(okValue.get(), "inspect must not be called on an Err");
        Assertions.assertTrue(errTriggered.get(), "inspectErr must see the problems of an Err");
    }

    @Test
    public void testPropagate() {
        final var innerErr = Result.err(p1);

        if (innerErr instanceof Result.Err<?> err) {
            Result<String> outerErr = err.propagate();
            Assertions.assertInstanceOf(Result.Err.class, outerErr, "a propagated Err must still be an Err");
            Result.Err<String> rawErr = (Result.Err<String>) outerErr;
            Assertions.assertEquals("Invalid token", rawErr.errors().get(0).reason, "a propagated Err must keep its problems");
        } else {
            Assertions.fail("Expected an Err instance to propagate");
        }
    }

    @Test
    public void testCollectProblems() {
        final var ok = Result.ok("Fine");
        final var err1 = Result.<Integer>err(p1);
        final var err2 = Result.<Double>err(p2);
        final var totalProblems = Result.collectProblems(ok, err1, err2);
        Assertions.assertEquals(2, totalProblems.size(), "the problems of every Err must be collected, none from an Ok");
        Assertions.assertEquals("Invalid token", totalProblems.get(0).reason, "problems must be collected in argument order");
        Assertions.assertEquals("Connection timed out", totalProblems.get(1).reason, "problems must be collected in argument order");
    }

    @Test
    public void problemsAreCollectedFromAnIterableInIterationOrder() {
        final List<Result<String>> results = List.of(Result.err(p2), Result.ok("Fine"), Result.err(p1));
        Assertions.assertEquals(List.of(p2, p1), Result.collectProblems(results), "the problems of every Err must be collected in iteration order");
    }

    @Test
    public void collectingFromOnlyOksYieldsNoProblems() {
        Assertions.assertEquals(List.of(), Result.collectProblems(Result.ok(1), Result.ok(2)), "no problem must be collected when every result is an Ok");
    }
}
