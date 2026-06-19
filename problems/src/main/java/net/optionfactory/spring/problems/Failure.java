package net.optionfactory.spring.problems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
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

    public static Failure of(@NonNull Problem problem) {
        return new Failure(List.of(problem), null, null);
    }

    public static Failure of(@NonNull List<Problem> problems) {
        return new Failure(problems, null, null);
    }

    public static Failure of(@NonNull String type, @Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.of(type, context, reason, details)), null, null);
    }

    public static Failure of(@NonNull String type, @Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.of(type, context, reason, Problem.NO_DETAILS)), null, null);
    }

    public static Failure of(@NonNull String type, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.of(type, Problem.NO_CONTEXT, reason, details)), null, null);
    }

    public static Failure of(@NonNull String type, @Nullable String reason) {
        return new Failure(List.of(Problem.of(type, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS)), null, null);
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

    public static Failure object(@Nullable String reason) {
        return new Failure(List.of(Problem.object(null, reason)), null, null);
    }

    public static Failure request(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.request(context, reason, details)), null, null);
    }

    public static Failure request(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.request(context, reason)), null, null);
    }

    public static Failure request(@Nullable String reason) {
        return new Failure(List.of(Problem.request(reason)), null, null);
    }

    public static Failure server(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.server(context, reason, details)), null, null);
    }

    public static Failure server(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.server(context, reason)), null, null);
    }

    public static Failure server(@Nullable String reason) {
        return new Failure(List.of(Problem.server(reason)), null, null);
    }

    public static Failure upstream(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.upstream(context, reason, details)), null, null);
    }

    public static Failure upstream(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.upstream(context, reason)), null, null);
    }

    public static Failure upstream(@Nullable String reason) {
        return new Failure(List.of(Problem.upstream(reason)), null, null);
    }

    public static Failure forbidden(@Nullable String reason) {
        return new Failure(List.of(Problem.forbidden(reason)), null, null);
    }

    public static Failure forbidden() {
        return new Failure(List.of(Problem.forbidden()), null, null);
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

        public Builder add(Supplier<@Nullable Problem> supplier) {
            final var p = supplier.get();
            if (p != null) {
                problems.add(p);
            }
            return this;
        }

        public Builder add(boolean test, Supplier<@Nullable Problem> supplier) {
            return test ? add(supplier) : this;
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
            problems.add(Problem.field(path, reason, details));
            return this;
        }

        public Builder field(@NonNull String path, @Nullable String reason) {
            problems.add(Problem.field(path, reason));
            return this;
        }

        public Builder object(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.object(context, reason, details));
            return this;
        }

        public Builder object(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.object(context, reason));
            return this;
        }

        public Builder object(@Nullable String reason) {
            problems.add(Problem.object(null, reason));
            return this;
        }

        public Builder request(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.request(context, reason, details));
            return this;
        }

        public Builder request(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.request(context, reason));
            return this;
        }

        public Builder request(@Nullable String reason) {
            problems.add(Problem.request(reason));
            return this;
        }

        public Builder server(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.server(context, reason, details));
            return this;
        }

        public Builder server(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.server(context, reason));
            return this;
        }

        public Builder server(@Nullable String reason) {
            problems.add(Problem.server(reason));
            return this;
        }

        public Builder upstream(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.upstream(context, reason, details));
            return this;
        }

        public Builder upstream(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.upstream(context, reason));
            return this;
        }

        public Builder upstream(@Nullable String reason) {
            problems.add(Problem.upstream(reason));
            return this;
        }

        public Builder forbidden(@Nullable String reason) {
            problems.add(Problem.forbidden(reason));
            return this;
        }

        public Builder forbidden() {
            problems.add(Problem.forbidden());
            return this;
        }

        public Failure build() {
            return new Failure(problems, cause, message);
        }

        public void enforce() {
            Failure.enforce(problems, cause, message);
        }

    }

    public static void enforce(List<Problem> problems, Throwable cause, String reason) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, cause, reason);
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

    public static void enforce(List<Problem> problems) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, null, null);
    }

}
