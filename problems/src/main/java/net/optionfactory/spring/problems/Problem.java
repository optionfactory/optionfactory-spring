package net.optionfactory.spring.problems;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class Problem {

    public static final String TYPE_FIELD_ERROR = "FIELD_ERROR";
    public static final String TYPE_OBJECT_ERROR = "OBJECT_ERROR";
    public static final String TYPE_REQUEST_ERROR = "REQUEST_ERROR";
    public static final String TYPE_SERVER_ERROR = "SERVER_ERROR";
    public static final String TYPE_UPSTREAM_ERROR = "UPSTREAM_ERROR";
    public static final String TYPE_FORBIDDEN = "FORBIDDEN";

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

    public static Problem of(@NonNull String type, @Nullable String context, @Nullable String reason) {
        return Problem.of(type, context, reason, Problem.NO_DETAILS);
    }

    public static Problem of(@NonNull String type, @Nullable String reason, @Nullable Object details) {
        return Problem.of(type, Problem.NO_CONTEXT, reason, details);
    }

    public static Problem of(@NonNull String type, @Nullable String reason) {
        return Problem.of(type, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
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

    public static Problem object(@Nullable String reason) {
        return of(Problem.TYPE_OBJECT_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    public static Problem request(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_REQUEST_ERROR, context, reason, details);
    }

    public static Problem request(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_REQUEST_ERROR, context, reason, Problem.NO_DETAILS);
    }

    public static Problem request(@Nullable String reason) {
        return of(Problem.TYPE_REQUEST_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    public static Problem server(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_SERVER_ERROR, context, reason, details);
    }

    public static Problem server(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_SERVER_ERROR, context, reason, Problem.NO_DETAILS);
    }

    public static Problem server(@Nullable String reason) {
        return of(Problem.TYPE_SERVER_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    public static Problem upstream(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_UPSTREAM_ERROR, context, reason, details);
    }

    public static Problem upstream(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_UPSTREAM_ERROR, context, reason, Problem.NO_DETAILS);
    }

    public static Problem upstream(@Nullable String reason) {
        return of(Problem.TYPE_UPSTREAM_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    public static Problem forbidden(@Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_FORBIDDEN, Problem.NO_CONTEXT, reason, details);
    }

    public static Problem forbidden(@Nullable String reason) {
        return of(Problem.TYPE_FORBIDDEN, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    public static Problem forbidden() {
        return of(Problem.TYPE_FORBIDDEN, Problem.NO_CONTEXT, null, Problem.NO_DETAILS);
    }

    @Override
    public String toString() {
        return String.format("%s@%s: %s (%s)", type, context, reason, details);
    }

}
