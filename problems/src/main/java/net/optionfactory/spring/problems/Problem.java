package net.optionfactory.spring.problems;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// One thing that went wrong, in the shape the problems libraries report errors to clients with:
///
/// ```json
/// {"type": "FIELD_ERROR", "context": "address.zip", "reason": "must not be blank", "details": null}
/// ```
///
/// - `type` classifies the problem, one of the `TYPE_` constants or an application-defined value;
/// - `context` locates it, e.g. the path of the offending field, or is `null` when the problem is
///   about the request or the object as a whole;
/// - `reason` says what is wrong, for the client to show;
/// - `details` carries anything else, typically for debugging: `problems-web` omits it from
///   responses unless configured otherwise.
///
/// A problem is a plain mutable carrier, its fields public so that serializers and the
/// `problems-web` transformers can rewrite them in place (localizing a reason, omitting details). It
/// does not override `equals` and `hashCode`: two problems are equal only when they are the same
/// instance. Problems are usually thrown in a [Failure], or returned in a [Result].
public class Problem {

    /// A problem with one field of the input, `context` being its path, dot separated.
    public static final String TYPE_FIELD_ERROR = "FIELD_ERROR";
    /// A problem with an input object as a whole, such as a violated class-level constraint.
    public static final String TYPE_OBJECT_ERROR = "OBJECT_ERROR";
    /// A problem with the request itself, such as an unreadable body or an unknown property.
    public static final String TYPE_REQUEST_ERROR = "REQUEST_ERROR";
    /// A failure of the server, not of the client's input.
    public static final String TYPE_SERVER_ERROR = "SERVER_ERROR";
    /// A failure of a service the server depends on.
    public static final String TYPE_UPSTREAM_ERROR = "UPSTREAM_ERROR";
    /// The caller is not allowed to do what it asked for.
    public static final String TYPE_FORBIDDEN = "FORBIDDEN";
    /// The caller is not authenticated, and must authenticate to do what it asked for.
    public static final String TYPE_UNAUTHORIZED = "UNAUTHORIZED";

    /// `null`, named for readability at call sites: the problem is not about a specific field.
    public static final String NO_CONTEXT = null;
    /// `null`, named for readability at call sites: the problem carries no details.
    public static final String NO_DETAILS = null;

    /// What kind of problem this is.
    public String type;
    /// Where the problem is, or `null`.
    public String context;
    /// What is wrong. `problems-web` treats it as a message code, localizing it when its message
    /// source knows it and keeping it as is otherwise.
    public String reason;
    /// Anything else, possibly structured, or `null`; not meant for production responses.
    public Object details;

    /// @param type the problem type
    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a new problem
    public static Problem of(@NonNull String type, @Nullable String context, @Nullable String reason, @Nullable Object details) {
        final Problem problem = new Problem();
        problem.type = type;
        problem.context = context;
        problem.reason = reason;
        problem.details = details;
        return problem;
    }

    /// @param type the problem type
    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a new problem without details
    public static Problem of(@NonNull String type, @Nullable String context, @Nullable String reason) {
        return Problem.of(type, context, reason, Problem.NO_DETAILS);
    }

    /// Beware: called with three `String`s, `of(type, reason, details)` resolves to
    /// [#of(String, String, String)] instead, which takes them as context and reason: pass `details`
    /// as an `Object` to reach this overload.
    ///
    /// @param type the problem type
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a new problem without context
    public static Problem of(@NonNull String type, @Nullable String reason, @Nullable Object details) {
        return Problem.of(type, Problem.NO_CONTEXT, reason, details);
    }

    /// @param type the problem type
    /// @param reason what is wrong
    /// @return a new problem without context and details
    public static Problem of(@NonNull String type, @Nullable String reason) {
        return Problem.of(type, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    /// @param path the path of the field, dot separated
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a [#TYPE_FIELD_ERROR] problem
    public static Problem field(@NonNull String path, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_FIELD_ERROR, path, reason, details);
    }

    /// @param path the path of the field, dot separated
    /// @param reason what is wrong
    /// @return a [#TYPE_FIELD_ERROR] problem without details
    public static Problem field(@NonNull String path, @Nullable String reason) {
        return of(Problem.TYPE_FIELD_ERROR, path, reason, Problem.NO_DETAILS);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a [#TYPE_OBJECT_ERROR] problem
    public static Problem object(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_OBJECT_ERROR, context, reason, details);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a [#TYPE_OBJECT_ERROR] problem without details
    public static Problem object(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_OBJECT_ERROR, context, reason, Problem.NO_DETAILS);
    }

    /// @param reason what is wrong
    /// @return a [#TYPE_OBJECT_ERROR] problem without context and details
    public static Problem object(@Nullable String reason) {
        return of(Problem.TYPE_OBJECT_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a [#TYPE_REQUEST_ERROR] problem
    public static Problem request(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_REQUEST_ERROR, context, reason, details);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a [#TYPE_REQUEST_ERROR] problem without details
    public static Problem request(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_REQUEST_ERROR, context, reason, Problem.NO_DETAILS);
    }

    /// @param reason what is wrong
    /// @return a [#TYPE_REQUEST_ERROR] problem without context and details
    public static Problem request(@Nullable String reason) {
        return of(Problem.TYPE_REQUEST_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return a [#TYPE_SERVER_ERROR] problem
    public static Problem server(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_SERVER_ERROR, context, reason, details);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return a [#TYPE_SERVER_ERROR] problem without details
    public static Problem server(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_SERVER_ERROR, context, reason, Problem.NO_DETAILS);
    }

    /// @param reason what is wrong
    /// @return a [#TYPE_SERVER_ERROR] problem without context and details
    public static Problem server(@Nullable String reason) {
        return of(Problem.TYPE_SERVER_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @param details anything else, or `null`
    /// @return an [#TYPE_UPSTREAM_ERROR] problem
    public static Problem upstream(@Nullable String context, @Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_UPSTREAM_ERROR, context, reason, details);
    }

    /// @param context where the problem is, or `null`
    /// @param reason what is wrong
    /// @return an [#TYPE_UPSTREAM_ERROR] problem without details
    public static Problem upstream(@Nullable String context, @Nullable String reason) {
        return of(Problem.TYPE_UPSTREAM_ERROR, context, reason, Problem.NO_DETAILS);
    }

    /// @param reason what is wrong
    /// @return an [#TYPE_UPSTREAM_ERROR] problem without context and details
    public static Problem upstream(@Nullable String reason) {
        return of(Problem.TYPE_UPSTREAM_ERROR, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    /// @param reason why the caller must authenticate
    /// @param details anything else, or `null`
    /// @return an [#TYPE_UNAUTHORIZED] problem without context
    public static Problem unauthorized(@Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_UNAUTHORIZED, Problem.NO_CONTEXT, reason, details);
    }

    /// @param reason why the caller is not allowed
    /// @param details anything else, or `null`
    /// @return a [#TYPE_FORBIDDEN] problem without context
    public static Problem forbidden(@Nullable String reason, @Nullable Object details) {
        return of(Problem.TYPE_FORBIDDEN, Problem.NO_CONTEXT, reason, details);
    }

    /// @param reason why the caller is not allowed
    /// @return a [#TYPE_FORBIDDEN] problem without context and details
    public static Problem forbidden(@Nullable String reason) {
        return of(Problem.TYPE_FORBIDDEN, Problem.NO_CONTEXT, reason, Problem.NO_DETAILS);
    }

    /// @return a [#TYPE_FORBIDDEN] problem without context, reason and details
    public static Problem forbidden() {
        return of(Problem.TYPE_FORBIDDEN, Problem.NO_CONTEXT, null, Problem.NO_DETAILS);
    }

    /// @return the problem as `type@context: reason (details)`, for logs and exception messages
    @Override
    public String toString() {
        return String.format("%s@%s: %s (%s)", type, context, reason, details);
    }

}
