package net.optionfactory.spring.problems;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FailureTest {

    @Test
    public void theExceptionMessageListsTheProblemsAndTheGivenMessage() {
        final var failure = new Failure(List.of(Problem.field("name", "required")), null, "signup");
        Assertions.assertEquals("problems (signup): [FIELD_ERROR@name: required (null)]", failure.getMessage(), "the message must include the given message and every problem");
    }

    @Test
    public void theExceptionMessageWithoutAGivenMessageListsTheProblems() {
        Assertions.assertEquals("problems : [FORBIDDEN@null: null (null)]", Failure.forbidden().getMessage(), "without a given message, the message must list the problems only");
    }

    @Test
    public void theCauseIsKept() {
        final var cause = new IllegalStateException("root");
        final var failure = Failure.builder().cause(cause).request("bad").build();
        Assertions.assertSame(cause, failure.getCause(), "the builder's cause must become the failure's cause");
    }

    @Test
    public void theBuilderKeepsProblemsInTheOrderTheyWereAdded() {
        final var failure = Failure.builder()
                .field("a", "first")
                .object("second")
                .server("ctx", "third")
                .build();
        Assertions.assertEquals(List.of("first", "second", "third"), failure.problems.stream().map(p -> p.reason).toList(), "problems must be kept in insertion order");
        Assertions.assertEquals(List.of(Problem.TYPE_FIELD_ERROR, Problem.TYPE_OBJECT_ERROR, Problem.TYPE_SERVER_ERROR), failure.problems.stream().map(p -> p.type).toList(), "each method must add a problem of its own type");
    }

    @Test
    public void aSupplierReturningNullAddsNothing() {
        final var failure = Failure.builder().add(() -> null).build();
        Assertions.assertEquals(List.of(), failure.problems, "a null problem from a supplier must be skipped");
    }

    @Test
    public void aConditionalProblemIsOnlyBuiltWhenTheCheckFails() {
        final var called = new AtomicBoolean(false);
        final var failure = Failure.builder().add(false, () -> {
            called.set(true);
            return Problem.field("name", "required");
        }).build();
        Assertions.assertFalse(called.get(), "the supplier must not be called when the check passed");
        Assertions.assertEquals(List.of(), failure.problems, "no problem must be added when the check passed");
    }

    @Test
    public void aConditionalProblemIsAddedWhenTheCheckFails() {
        final var failure = Failure.builder().add(true, () -> Problem.field("name", "required")).build();
        Assertions.assertEquals(List.of("name"), failure.problems.stream().map(p -> p.context).toList(), "the supplied problem must be added when the check failed");
    }

    @Test
    public void problemsAddedAfterBuildingDoNotShowUpInTheBuiltFailure() {
        final var builder = Failure.builder().field("name", "required");
        final var failure = builder.build();
        builder.field("surname", "required");
        Assertions.assertEquals(1, failure.problems.size(), "a built failure must keep only the problems collected before it was built");
        final var thrown = Assertions.assertThrows(Failure.class, builder::enforce, "enforcing with problems must throw");
        builder.field("email", "required");
        Assertions.assertEquals(2, thrown.problems.size(), "a thrown failure must keep only the problems collected before it was thrown");
    }

    @Test
    public void enforcingAnEmptyBuilderDoesNotThrow() {
        Assertions.assertDoesNotThrow(() -> Failure.builder().add(false, () -> Problem.field("name", "required")).enforce(), "enforcing without problems must not throw");
    }

    @Test
    public void enforcingABuilderWithProblemsThrowsThem() {
        final var failure = Assertions.assertThrows(Failure.class, () -> Failure.builder().message("signup").field("name", "required").enforce(), "enforcing with problems must throw");
        Assertions.assertEquals("name", failure.problems.get(0).context, "the thrown failure must carry the collected problems");
        Assertions.assertTrue(failure.getMessage().startsWith("problems (signup)"), "the thrown failure must carry the builder's message");
    }

    @Test
    public void staticEnforceThrowsOnlyWhenThereAreProblems() {
        Assertions.assertDoesNotThrow(() -> Failure.enforce(List.of(), "nothing"), "enforcing an empty list must not throw");
        final var cause = new IllegalStateException("root");
        final var failure = Assertions.assertThrows(Failure.class, () -> Failure.enforce(List.of(Problem.request("bad")), cause), "enforcing a non-empty list must throw");
        Assertions.assertSame(cause, failure.getCause(), "the thrown failure must carry the given cause");
    }

    @Test
    public void singleProblemFactoriesFillTypeAndContext() {
        final var field = Failure.field("address.zip", "required").problems.get(0);
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, field.type, "field must build a FIELD_ERROR");
        Assertions.assertEquals("address.zip", field.context, "field must use the path as context");
        final var upstream = Failure.upstream("down").problems.get(0);
        Assertions.assertEquals(Problem.TYPE_UPSTREAM_ERROR, upstream.type, "upstream must build an UPSTREAM_ERROR");
        Assertions.assertNull(upstream.context, "a factory without context must leave it null");
        final var custom = Failure.of("CONFLICT", "already exists", (Object) "id=1").problems.get(0);
        Assertions.assertEquals("already exists", custom.reason, "of(type, reason, details) must set the reason");
        Assertions.assertEquals("id=1", custom.details, "of(type, reason, details) must set the details");
        Assertions.assertNull(custom.context, "of(type, reason, details) must leave the context null");
    }
}
