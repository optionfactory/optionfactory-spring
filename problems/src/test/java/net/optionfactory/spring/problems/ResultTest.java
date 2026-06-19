package net.optionfactory.spring.problems;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;

public class ResultTest {

    private final Problem p1 = Problem.request("auth-context", "Invalid token");
    private final Problem p2 = Problem.server("database-context", "Connection timed out");

    @Test
    public void testOkLifecycle() {
        final var result = Result.ok("Success");
        if (result instanceof Result.Ok(var value)) {
            Assertions.assertEquals("Success", value);
        } else {
            Assertions.fail("Expected Result.Ok");
        }
        Assertions.assertEquals("Success", result.unwrap());
        Assertions.assertEquals("Success", result.unwrapOr("Fallback"));
        Assertions.assertEquals("Success", result.unwrapOrElse(() -> "Lazy Fallback"));
    }

    @Test
    public void testErrLifecycle() {
        final var result = Result.err(p1, p2);

        if (result instanceof Result.Err(var errors)) {
            Assertions.assertEquals(2, errors.size());
            Assertions.assertEquals("Invalid token", errors.get(0).reason);
            Assertions.assertEquals("auth-context", errors.get(0).context);
            Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, errors.get(0).type);
        } else {
            Assertions.fail("Expected Result.Err");
        }

        Assertions.assertThrows(Failure.class, result::unwrap);
        Assertions.assertEquals("Fallback", result.unwrapOr("Fallback"));
        Assertions.assertEquals("Lazy Fallback", result.unwrapOrElse(() -> "Lazy Fallback"));
    }

    @Test
    public void testMap() {
        final var okMapped = Result.ok("123").map(Integer::parseInt);
        Assertions.assertEquals(Integer.valueOf(123), okMapped.unwrap());
        final var err = Result.<String>err(p1);
        final var errMapped = err.map(Integer::parseInt);

        Assertions.assertInstanceOf(Result.Err.class, errMapped);
        final var narrowed = (Result.Err<Integer>) errMapped;
        Assertions.assertEquals(1, narrowed.errors().size());
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, narrowed.errors().get(0).type);
    }

    @Test
    public void testFlatMap() {
        final var chainSuccess = Result.ok("User123")
                .flatMap(name -> Result.ok(name.length()));
        Assertions.assertEquals(Integer.valueOf(7), chainSuccess.unwrap());

        final var chainFailure = Result.ok("User123")
                .flatMap(name -> Result.err(p1));
        Assertions.assertInstanceOf(Result.Err.class, chainFailure);

        final var err = Result.<String>err(p1);
        final var bypassed = err.flatMap(name -> Result.ok(name.length()));
        Assertions.assertInstanceOf(Result.Err.class, bypassed);
    }

    @Test
    public void testMapErr() {
        final var err = Result.err(p1);
        final var transformedErr = err.mapErr(errors -> List.of(p2));

        if (transformedErr instanceof Result.Err(var errors)) {
            Assertions.assertEquals(1, errors.size());
            Assertions.assertEquals("Connection timed out", errors.get(0).reason);
            Assertions.assertEquals(Problem.TYPE_SERVER_ERROR, errors.get(0).type);
        } else {
            Assertions.fail("Expected Result.Err");
        }

        final var ok = Result.ok("Safe");
        final var bypassed = ok.mapErr(errors -> List.of(p2));
        Assertions.assertEquals("Safe", bypassed.unwrap());
    }

    @Test
    public void testInspectAndInspectErr() {
        final var okValue = new AtomicReference<String>();
        final var errTriggered = new AtomicBoolean(false);

        Result.ok("Data")
                .inspect(okValue::set)
                .inspectErr(errors -> errTriggered.set(true));

        Assertions.assertEquals("Data", okValue.get());
        Assertions.assertFalse(errTriggered.get());

        okValue.set(null);
        Result.<String>err(p1)
                .inspect(okValue::set)
                .inspectErr(errors -> errTriggered.set(true));

        Assertions.assertNull(okValue.get());
        Assertions.assertTrue(errTriggered.get());
    }

    @Test
    public void testPropagate() {
        final var innerErr = Result.err(p1);

        if (innerErr instanceof Result.Err<?> err) {
            Result<String> outerErr = err.propagate();
            Assertions.assertInstanceOf(Result.Err.class, outerErr);
            Result.Err<String> rawErr = (Result.Err<String>) outerErr;
            Assertions.assertEquals("Invalid token", rawErr.errors().get(0).reason);
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
        Assertions.assertEquals(2, totalProblems.size());
        Assertions.assertEquals("Invalid token", totalProblems.get(0).reason);
        Assertions.assertEquals("Connection timed out", totalProblems.get(1).reason);
    }
}
