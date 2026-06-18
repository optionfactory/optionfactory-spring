package net.optionfactory.spring.problems;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class Failure extends RuntimeException {

    public final List<Problem> problems;

    public Failure(List<Problem> problems, @Nullable Throwable cause, @Nullable String message) {
        super("problems %s: %s".formatted(message == null ? "" : "(" + message + ")", problems), cause);
        this.problems = problems;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Failure of(@NonNull String type, @Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.of(type, context, reason, details)), null, null);
    }

    public static Failure field(@NonNull String path, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.field(path, reason, details)), null, null);
    }

    public static Failure field(@NonNull String path, @Nullable String reason) {
        return new Failure(List.of(Problem.field(path, reason)), null, null);
    }

    public static Failure object(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.object(context, reason, details)), null, null);
    }

    public static Failure object(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.object(context, reason)), null, null);
    }

    public static class Builder {

        private Throwable cause;
        private String message;
        private final List<Problem> problems = new ArrayList<>();

        public Builder cause(@Nullable Throwable t) {
            this.cause = t;
            return this;
        }

        public Builder message(@Nullable String m) {
            this.message = m;
            return this;
        }

        public Builder add(Problem p) {
            problems.add(p);
            return this;
        }

        public Builder add(@NonNull String type, @Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.of(type, context, reason, details));
            return this;
        }

        public Builder field(@NonNull String path, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.of(Problem.TYPE_FIELD_ERROR, path, reason, details));
            return this;
        }

        public Builder field(@NonNull String path, @Nullable String reason) {
            problems.add(Problem.of(Problem.TYPE_FIELD_ERROR, path, reason, Problem.NO_DETAILS));
            return this;
        }

        public Builder object(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.of(Problem.TYPE_OBJECT_ERROR, context, reason, details));
            return this;
        }

        public Builder object(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.of(Problem.TYPE_OBJECT_ERROR, context, reason, Problem.NO_DETAILS));
            return this;
        }

        public Failure build() {
            return new Failure(problems, cause, message);
        }

    }

    public static void enforce(List<Problem> problems) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, null, null);
    }

    public static void enforce(List<Problem> problems, String reason) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, null, reason);
    }

    public static void enforce(List<Problem> problems, Throwable cause) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, cause, null);
    }
}
