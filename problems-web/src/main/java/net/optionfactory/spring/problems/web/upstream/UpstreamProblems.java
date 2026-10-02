package net.optionfactory.spring.problems.web.upstream;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.HttpStatus;
import net.optionfactory.spring.problems.web.upstream.UpstreamProblems.MapContext.MapContextList;

/// The declarations a handler method uses to answer the failure of an upstream it called, applied
/// by [UpstreamFailureTransformer].
///
/// An upstream built with `upstream`, failing with a `RestClientUpstreamException`, is answered by
/// default with a `502` and a single `UPSTREAM_ERROR` problem. When the upstream is itself a
/// problems-speaking service that rejected the client's input, the handler can pass its problems
/// on instead:
///
/// ```java
/// @PostMapping("/orders")
/// @UpstreamProblems.Forward(upstream = "warehouse", source = HttpStatus.BAD_REQUEST, target = HttpStatus.BAD_REQUEST)
/// @UpstreamProblems.MapContext(upstream = "warehouse", source = "reservation.")
/// public Order create(@RequestBody OrderRequest request) {
///     return warehouse.reserve(request);
/// }
/// ```
///
/// Here a `400` from the warehouse is answered `400` with the warehouse's problems, a context of
/// `reservation.quantity` becoming `quantity`.
public interface UpstreamProblems {

    /// Forwards the failure of an upstream, with another status and, by default, the upstream's
    /// own problems.
    ///
    /// The forward applies when the failure comes from the [#upstream()] and [#endpoint()] named,
    /// when they are, with the [#source()] status; the upstream's response body is then read as a
    /// list of problems, unless [#problems()] is false.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Forward {

        /// @return the name of the upstream whose failures are forwarded, empty for any upstream
        String upstream() default "";

        /// @return the name of the upstream endpoint whose failures are forwarded, empty for any
        ///         endpoint
        String endpoint() default "";

        /// @return the upstream response status to forward
        HttpStatus source() default HttpStatus.BAD_REQUEST;

        /// @return the status to answer the client with
        HttpStatus target();

        /// @return true to answer with the upstream's problems, read from its response body; false to
        ///         change the status only, keeping the `UPSTREAM_ERROR` problem
        boolean problems() default true;
    }

    /// How [MapContext#source()] is matched in a problem's context and replaced by
    /// [MapContext#target()].
    public enum MapMode {
        /// The first match of a regular expression is replaced; the target may refer to its groups,
        /// as `$1`.
        REGEX_FIRST,
        /// Every match of a regular expression is replaced; the target may refer to its groups, as
        /// `$1`.
        REGEX_ALL,
        /// Every occurrence of a literal string is replaced.
        STRING_ALL,
        /// The first occurrence of a literal string is replaced.
        STRING_FIRST;
    }

    /// Rewrites the contexts of the problems answering an upstream's failure, typically to turn the
    /// upstream's names for the fields into the client's.
    ///
    /// The mapping applies to the failures of the [#upstream()] and [#endpoint()] named, when they
    /// are, whatever the status, and rewrites the context of every problem that has one, in place.
    /// It is meant for problems forwarded with [Forward]: the default `UPSTREAM_ERROR` problem has no
    /// context to rewrite. Repeatable: the mappings apply in declaration order.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(MapContextList.class)
    public @interface MapContext {

        /// @return the name of the upstream whose problems are mapped, empty for any upstream
        String upstream() default "";

        /// @return the name of the upstream endpoint whose problems are mapped, empty for any
        ///         endpoint
        String endpoint() default "";

        /// @return how [#source()] is matched and replaced
        MapMode mode() default MapMode.STRING_FIRST;

        /// @return the string or regular expression to find in a context
        String source();

        /// @return the replacement, empty to remove what [#source()] matched
        String target() default "";

        /// Holds the repeated [MapContext] declarations of a method.
        @Documented
        @Target(value = ElementType.METHOD)
        @Retention(value = RetentionPolicy.RUNTIME)
        public static @interface MapContextList {

            /// @return the mappings, in declaration order
            MapContext[] value();
        }

    }
}
