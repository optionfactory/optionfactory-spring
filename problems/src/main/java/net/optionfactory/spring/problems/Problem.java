package net.optionfactory.spring.problems;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class Problem {
    public static final String TYPE_FIELD_ERROR = "FIELD_ERROR";
    public static final String TYPE_OBJECT_ERROR = "OBJECT_ERROR";
    
    public static final String NO_CONTEXT = null;
    public static final String NO_DETAILS = null;

    public String type;
    public String context;
    public String reason;
    public Object details;

    public static Problem of(@NonNull String type, @Nullable String context, @Nullable String reason, @Nullable Object details) {
        final Problem problem = new Problem();
        problem.type = type;
        problem.context = context;
        problem.reason = reason;
        problem.details = details;
        return problem;
    }
    
    public static Problem field(@NonNull String path, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_FIELD_ERROR, path, reason, details);
    }

    public static Problem field(@NonNull String path, @Nullable String reason) {
        return of(Problem.TYPE_FIELD_ERROR, path, reason, Problem.NO_DETAILS);
    }

    public static Problem object(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_OBJECT_ERROR, context, reason, details);
    }

    public static Problem object(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_OBJECT_ERROR, context, reason, Problem.NO_DETAILS);
    }
    
    
    @Override
    public String toString() {
        return String.format("%s@%s: %s (%s)", type, context, reason, details);
    }

}
