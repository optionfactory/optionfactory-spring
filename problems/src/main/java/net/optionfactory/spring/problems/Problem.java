package net.optionfactory.spring.problems;

public class Problem {

    public static final String NO_CONTEXT = null;
    public static final String NO_DETAILS = null;

    public String type;
    public String context;
    public String reason;
    public Object details;

    public static Problem of(String type, String context, String reason, Object details) {
        final Problem problem = new Problem();
        problem.type = type;
        problem.context = context;
        problem.reason = reason;
        problem.details = details;
        return problem;
    }

    public static Problem of(String type, String reason) {
        final Problem problem = new Problem();
        problem.type = type;
        problem.context = NO_CONTEXT;
        problem.reason = reason;
        problem.details = NO_DETAILS;
        return problem;
    }

    @Override
    public String toString() {
        return String.format("%s@%s: %s (%s)", type, context, reason, details);
    }

}
