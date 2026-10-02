package net.optionfactory.spring.problems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// An unchecked exception carrying the [Problem]s that made an operation fail, for code that
/// reports errors by throwing.
///
/// `problems-web` answers a failure thrown by a handler with its problems, as a `400` unless the
/// failure's class declares another status with `@ResponseStatus`, localizing each problem's reason
/// as a message code. Subclass it to give a kind of failure its own status.
///
/// The static factories build a failure with a single problem; [#builder()] collects several,
/// typically while validating input, and [Builder#enforce()] throws only when something was
/// collected:
///
/// ```java
/// Failure.builder()
///         .add(name.isBlank(), () -> Problem.field("name", "must not be blank"))
///         .add(age < 18, () -> Problem.field("age", "must be an adult"))
///         .enforce();
/// ```
///
/// The exception message is `problems (message): [problems...]`, or `problems : [problems...]`
/// without a message, so that logs show what failed.
public class Failure extends RuntimeException {

    /// The problems, in the order they were given. The list is the one passed to the constructor,
    /// not a copy, while a [Builder] passes a copy of its own: it is mutable whenever that list is,
    /// and `problems-web` rewrites the problems' reasons in place when it localizes them.
    public final List<Problem> problems;

    /// @param problems the problems, kept as given
    /// @param cause the exception that caused the failure, or `null`
    /// @param message a description to include in the exception message, or `null`
    public Failure(List<Problem> problems, @Nullable Throwable cause, @Nullable String message) {
        super("problems %s: %s".formatted(message == null ? "" : "(" + message + ")", problems), cause);
        this.problems = problems;
    }

    /// @return a builder collecting problems into a failure
    public static Builder builder() {
        return new Builder();
    }

    /// @param problem the problems, none of them `null`
    /// @return a failure with the given problems, an unmodifiable list
    public static Failure of(@NonNull Problem... problem) {
        return new Failure(List.of(problem), null, null);
    }

    /// @param problems the problems, kept as given
    /// @return a failure with the given problems
    public static Failure of(@NonNull List<Problem> problems) {
        return new Failure(problems, null, null);
    }

    /// @param type the problem type
    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a failure with a single problem
    public static Failure of(@NonNull String type, @Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.of(type, context, reason, details)), null, null);
    }

    /// @param type the problem type
    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a failure with a single problem without details
    public static Failure of(@NonNull String type, @Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.of(type, context, reason, Problem.NO_DETAILS)), null, null);
    }

    /// Beware: called with three `String`s, `of(type, reason, details)` resolves to
    /// [#of(String, String, String)] instead, which takes them as context and reason: pass `details`
    /// as an `Object` to reach this overload.
    ///
    /// @param type the problem type
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a failure with a single problem without context
    public static Failure of(@NonNull String type, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.of(type, Problem.NO_CONTEXT, reason, details)), null, null);
    }

    /// @param type the problem type
    /// @param reason what is wrong
    /// @return a failure with a single problem without context and details
    public static Failure of(@NonNull String type, @Nullable String reason) {
        return new Failure(List.of(Problem.of(type, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS)), null, null);
    }

    /// @param path the path of the field, dot separated
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a failure with a `FIELD_ERROR` problem
    public static Failure field(@NonNull String path, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.field(path, reason, details)), null, null);
    }

    /// @param path the path of the field, dot separated
    /// @param reason what is wrong
    /// @return a failure with a `FIELD_ERROR` problem without details
    public static Failure field(@NonNull String path, @Nullable String reason) {
        return new Failure(List.of(Problem.field(path, reason)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a failure with an `OBJECT_ERROR` problem
    public static Failure object(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.object(context, reason, details)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a failure with an `OBJECT_ERROR` problem without details
    public static Failure object(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.object(context, reason)), null, null);
    }

    /// @param reason what is wrong
    /// @return a failure with an `OBJECT_ERROR` problem without context and details
    public static Failure object(@Nullable String reason) {
        return new Failure(List.of(Problem.object(null, reason)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a failure with a `REQUEST_ERROR` problem
    public static Failure request(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.request(context, reason, details)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a failure with a `REQUEST_ERROR` problem without details
    public static Failure request(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.request(context, reason)), null, null);
    }

    /// @param reason what is wrong
    /// @return a failure with a `REQUEST_ERROR` problem without context and details
    public static Failure request(@Nullable String reason) {
        return new Failure(List.of(Problem.request(reason)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a failure with a `SERVER_ERROR` problem
    public static Failure server(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.server(context, reason, details)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a failure with a `SERVER_ERROR` problem without details
    public static Failure server(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.server(context, reason)), null, null);
    }

    /// @param reason what is wrong
    /// @return a failure with a `SERVER_ERROR` problem without context and details
    public static Failure server(@Nullable String reason) {
        return new Failure(List.of(Problem.server(reason)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a failure with an `UPSTREAM_ERROR` problem
    public static Failure upstream(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return new Failure(List.of(Problem.upstream(context, reason, details)), null, null);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a failure with an `UPSTREAM_ERROR` problem without details
    public static Failure upstream(@Nullable String context, @Nullable String reason) {
        return new Failure(List.of(Problem.upstream(context, reason)), null, null);
    }

    /// @param reason what is wrong
    /// @return a failure with an `UPSTREAM_ERROR` problem without context and details
    public static Failure upstream(@Nullable String reason) {
        return new Failure(List.of(Problem.upstream(reason)), null, null);
    }

    /// @param reason why the caller is not allowed
    /// @return a failure with a `FORBIDDEN` problem without context and details
    public static Failure forbidden(@Nullable String reason) {
        return new Failure(List.of(Problem.forbidden(reason)), null, null);
    }

    /// @return a failure with a `FORBIDDEN` problem without context, reason and details
    public static Failure forbidden() {
        return new Failure(List.of(Problem.forbidden()), null, null);
    }

    /// Collects problems, then builds or throws a [Failure] with them.
    ///
    /// Problems are kept in the order they are added. A builder is not thread-safe. Each failure it
    /// builds or throws gets a mutable copy of the problems collected so far: problems added
    /// afterwards do not show up in the failures already built.
    public static class Builder {

        private Throwable cause;
        private String message;
        private final List<Problem> problems = new ArrayList<>();

        /// @param t the exception that caused the failure, or `null`
        /// @return this builder
        public Builder cause(@Nullable Throwable t) {
            this.cause = t;
            return this;
        }

        /// @param m a description to include in the exception message, or `null`
        /// @return this builder
        public Builder message(@Nullable String m) {
            this.message = m;
            return this;
        }

        /// Adds the problem the supplier returns, if any.
        ///
        /// @param supplier the problem to add, `null` to add nothing
        /// @return this builder
        public Builder add(Supplier<@Nullable Problem> supplier) {
            final var p = supplier.get();
            if (p != null) {
                problems.add(p);
            }
            return this;
        }

        /// Adds a problem when a check fails. The supplier is only called when `test` is true, so it
        /// can build its problem from values that are only meaningful when the check failed.
        ///
        /// @param test true when the problem occurred
        /// @param supplier the problem to add, `null` to add nothing
        /// @return this builder
        public Builder add(boolean test, Supplier<@Nullable Problem> supplier) {
            return test ? add(supplier) : this;
        }

        /// @param p the problem to add
        /// @return this builder
        public Builder add(Problem p) {
            problems.add(p);
            return this;
        }

        /// @param type the problem type
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @param details anything else, or `null`
        /// @return this builder
        public Builder add(@NonNull String type, @Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.of(type, context, reason, details));
            return this;
        }

        /// Adds a `FIELD_ERROR` problem.
        ///
        /// @param path the path of the field, dot separated
        /// @param reason what is wrong
        /// @param details anything else, or `null`
        /// @return this builder
        public Builder field(@NonNull String path, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.field(path, reason, details));
            return this;
        }

        /// Adds a `FIELD_ERROR` problem without details.
        ///
        /// @param path the path of the field, dot separated
        /// @param reason what is wrong
        /// @return this builder
        public Builder field(@NonNull String path, @Nullable String reason) {
            problems.add(Problem.field(path, reason));
            return this;
        }

        /// Adds an `OBJECT_ERROR` problem.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @param details anything else, or `null`
        /// @return this builder
        public Builder object(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.object(context, reason, details));
            return this;
        }

        /// Adds an `OBJECT_ERROR` problem without details.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @return this builder
        public Builder object(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.object(context, reason));
            return this;
        }

        /// Adds an `OBJECT_ERROR` problem without context and details.
        ///
        /// @param reason what is wrong
        /// @return this builder
        public Builder object(@Nullable String reason) {
            problems.add(Problem.object(null, reason));
            return this;
        }

        /// Adds a `REQUEST_ERROR` problem.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @param details anything else, or `null`
        /// @return this builder
        public Builder request(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.request(context, reason, details));
            return this;
        }

        /// Adds a `REQUEST_ERROR` problem without details.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @return this builder
        public Builder request(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.request(context, reason));
            return this;
        }

        /// Adds a `REQUEST_ERROR` problem without context and details.
        ///
        /// @param reason what is wrong
        /// @return this builder
        public Builder request(@Nullable String reason) {
            problems.add(Problem.request(reason));
            return this;
        }

        /// Adds a `SERVER_ERROR` problem.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @param details anything else, or `null`
        /// @return this builder
        public Builder server(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.server(context, reason, details));
            return this;
        }

        /// Adds a `SERVER_ERROR` problem without details.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @return this builder
        public Builder server(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.server(context, reason));
            return this;
        }

        /// Adds a `SERVER_ERROR` problem without context and details.
        ///
        /// @param reason what is wrong
        /// @return this builder
        public Builder server(@Nullable String reason) {
            problems.add(Problem.server(reason));
            return this;
        }

        /// Adds an `UPSTREAM_ERROR` problem.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @param details anything else, or `null`
        /// @return this builder
        public Builder upstream(@Nullable String context, @Nullable String reason, @Nullable Object details) {
            problems.add(Problem.upstream(context, reason, details));
            return this;
        }

        /// Adds an `UPSTREAM_ERROR` problem without details.
        ///
        /// @param context where the problem is, or `null`
        /// @param reason what is wrong
        /// @return this builder
        public Builder upstream(@Nullable String context, @Nullable String reason) {
            problems.add(Problem.upstream(context, reason));
            return this;
        }

        /// Adds an `UPSTREAM_ERROR` problem without context and details.
        ///
        /// @param reason what is wrong
        /// @return this builder
        public Builder upstream(@Nullable String reason) {
            problems.add(Problem.upstream(reason));
            return this;
        }

        /// Adds a `FORBIDDEN` problem without context and details.
        ///
        /// @param reason why the caller is not allowed
        /// @return this builder
        public Builder forbidden(@Nullable String reason) {
            problems.add(Problem.forbidden(reason));
            return this;
        }

        /// Adds a `FORBIDDEN` problem without context, reason and details.
        ///
        /// @return this builder
        public Builder forbidden() {
            problems.add(Problem.forbidden());
            return this;
        }

        /// Builds the failure whatever was collected, even nothing: use [#enforce()] to throw only
        /// when there are problems.
        ///
        /// @return a failure with the problems, cause and message collected
        public Failure build() {
            return new Failure(new ArrayList<>(problems), cause, message);
        }

        /// Throws the failure when any problem was collected, does nothing otherwise.
        ///
        /// @throws Failure with the problems, cause and message collected, when there are problems
        public void enforce() {
            Failure.enforce(new ArrayList<>(problems), cause, message);
        }

    }

    /// Throws a failure with the given problems, unless there are none.
    ///
    /// @param problems the problems
    /// @param cause the exception that caused the failure, or `null`
    /// @param reason a description to include in the exception message, or `null`
    /// @throws Failure with the problems, when `problems` is not empty
    public static void enforce(List<Problem> problems, Throwable cause, String reason) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, cause, reason);
    }

    /// Throws a failure with the given problems, unless there are none.
    ///
    /// @param problems the problems
    /// @param reason a description to include in the exception message, or `null`
    /// @throws Failure with the problems, when `problems` is not empty
    public static void enforce(List<Problem> problems, String reason) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, null, reason);
    }

    /// Throws a failure with the given problems, unless there are none.
    ///
    /// @param problems the problems
    /// @param cause the exception that caused the failure, or `null`
    /// @throws Failure with the problems, when `problems` is not empty
    public static void enforce(List<Problem> problems, Throwable cause) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, cause, null);
    }

    /// Throws a failure with the given problems, unless there are none.
    ///
    /// @param problems the problems
    /// @throws Failure with the problems, when `problems` is not empty
    public static void enforce(List<Problem> problems) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, null, null);
    }

}
