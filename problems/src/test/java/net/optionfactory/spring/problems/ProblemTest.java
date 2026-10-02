package net.optionfactory.spring.problems;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProblemTest {

    @Test
    public void factoriesFillTheTypeOfTheirKind() {
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, Problem.field("a", "r").type, "field must build a FIELD_ERROR");
        Assertions.assertEquals(Problem.TYPE_OBJECT_ERROR, Problem.object("r").type, "object must build an OBJECT_ERROR");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, Problem.request("r").type, "request must build a REQUEST_ERROR");
        Assertions.assertEquals(Problem.TYPE_SERVER_ERROR, Problem.server("r").type, "server must build a SERVER_ERROR");
        Assertions.assertEquals(Problem.TYPE_UPSTREAM_ERROR, Problem.upstream("r").type, "upstream must build an UPSTREAM_ERROR");
        Assertions.assertEquals(Problem.TYPE_FORBIDDEN, Problem.forbidden().type, "forbidden must build a FORBIDDEN");
    }

    @Test
    public void threeStringsAreReadAsTypeContextAndReason() {
        final var problem = Problem.of("T", "first", "second");
        Assertions.assertEquals("first", problem.context, "with three strings, the second must be the context");
        Assertions.assertEquals("second", problem.reason, "with three strings, the third must be the reason");
        Assertions.assertNull(problem.details, "with three strings, there must be no details");
    }

    @Test
    public void objectDetailsSelectTheOverloadWithoutContext() {
        final var problem = Problem.of("T", "reason", (Object) "details");
        Assertions.assertNull(problem.context, "with Object details, there must be no context");
        Assertions.assertEquals("reason", problem.reason, "with Object details, the second argument must be the reason");
        Assertions.assertEquals("details", problem.details, "with Object details, the third argument must be the details");
    }

    @Test
    public void theStringFormIsTypeAtContextReasonAndDetails() {
        Assertions.assertEquals("FIELD_ERROR@name: required (42)", Problem.field("name", "required", 42).toString(), "toString must read type@context: reason (details)");
    }
}
