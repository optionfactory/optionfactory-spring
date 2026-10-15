package net.optionfactory.spring.problems.web.upstream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.FailureTransformer;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.method.HandlerMethod;

/// Applies a handler's [UpstreamProblems.Forward] and [UpstreamProblems.MapContext] declarations
/// to the failure of an upstream call it made, a `RestClientUpstreamException`.
///
/// By default an upstream failure is answered as a `502` with an `UPSTREAM_ERROR` problem: the
/// client is told that something behind the server failed, not what. When the upstream rejected
/// the request because of the client's own input, a handler can forward the upstream's problems
/// instead, with [UpstreamProblems.Forward], and rewrite their contexts to name the client's input
/// rather than the upstream's, with [UpstreamProblems.MapContext]. The forward is applied first,
/// then every matching mapping, in declaration order, each on the result of the previous. Mapping
/// builds new problems, leaving the ones it was given unchanged.
///
/// Any other exception, and an upstream failure on a handler declaring neither, is returned
/// unchanged. Registered by default through [UpstreamProblemsModule].
public class UpstreamFailureTransformer implements FailureTransformer {

    private static final ParameterizedTypeReference<List<Problem>> PROBLEMS_LIST = new ParameterizedTypeReference<>() {
    };
    private final Map<HandlerMethod, List<ContextMapping>> mappings = new ConcurrentHashMap<>();
    private final Map<HandlerMethod, Optional<UpstreamProblems.Forward>> statuses = new ConcurrentHashMap<>();

    /// @param saps the status and problems answered so far
    /// @param request the current request, unused
    /// @param response the current response, unused
    /// @param handler the handler method, whose declarations are read once and cached
    /// @param ex the exception being answered
    /// @return the forwarded status and problems, contexts mapped, or `saps` when nothing applies
    @Override
    public HttpStatusAndProblems transform(HttpStatusAndProblems saps, HttpServletRequest request, HttpServletResponse response, HandlerMethod handler, Exception ex) {
        if (!(ex instanceof RestClientUpstreamException uex)) {
            return saps;
        }
        final var w1 = applyForward(saps, handler, uex);
        final var w2 = applyMappings(w1, handler, uex);
        return w2;
    }

    private HttpStatusAndProblems applyForward(HttpStatusAndProblems saps, HandlerMethod handler, RestClientUpstreamException uex) {
        final var maybeAnnotation = statuses.computeIfAbsent(handler, (hm) -> Optional.ofNullable(hm.getMethodAnnotation(UpstreamProblems.Forward.class)));
        if (maybeAnnotation.isEmpty()) {
            return saps;
        }
        final var annotation = maybeAnnotation.get();
        if (!annotation.upstream().isEmpty() && !annotation.upstream().equals(uex.upstream)) {
            return saps;
        }
        if (!annotation.endpoint().isEmpty() && !annotation.endpoint().equals(uex.endpoint)) {
            return saps;
        }
        if (annotation.source().value() != uex.getStatusCode().value()) {
            return saps;
        }

        if (!annotation.problems()) {
            return new HttpStatusAndProblems(annotation.target(), saps.problems());
        }
        return new HttpStatusAndProblems(annotation.target(), uex.getResponseBodyAs(PROBLEMS_LIST));
    }

    private HttpStatusAndProblems applyMappings(HttpStatusAndProblems saps, HandlerMethod handler, RestClientUpstreamException uex) {
        final var handlerMappings = mappings.computeIfAbsent(handler, (hm) -> Stream.of(hm.getMethod().getAnnotationsByType(UpstreamProblems.MapContext.class)).map(ContextMapping::of).toList());
        if (handlerMappings.isEmpty()) {
            return saps;
        }
        var problems = saps.problems();
        for (final var mapping : handlerMappings) {
            if (!mapping.appliesTo(uex)) {
                continue;
            }
            problems = problems.stream().map(mapping::apply).toList();
        }
        return new HttpStatusAndProblems(saps.status(), problems);
    }

    /// A [UpstreamProblems.MapContext] declaration, its regular expression compiled once when the
    /// mode is `REGEX_FIRST` or `REGEX_ALL`, `null` otherwise.
    private record ContextMapping(UpstreamProblems.MapContext annotation, @Nullable Pattern pattern) {

        static ContextMapping of(UpstreamProblems.MapContext annotation) {
            return new ContextMapping(annotation, switch (annotation.mode()) {
                case REGEX_FIRST, REGEX_ALL ->
                    Pattern.compile(annotation.source());
                case STRING_FIRST, STRING_ALL ->
                    null;
            });
        }

        boolean appliesTo(RestClientUpstreamException uex) {
            if (!annotation.upstream().isEmpty() && !annotation.upstream().equals(uex.upstream)) {
                return false;
            }
            return annotation.endpoint().isEmpty() || annotation.endpoint().equals(uex.endpoint);
        }

        Problem apply(Problem problem) {
            final String context = problem.context();
            if (context == null) {
                return problem;
            }
            return problem.withContext(switch (annotation.mode()) {
                case STRING_FIRST -> {
                    int index = context.indexOf(annotation.source());
                    if (index < 0) {
                        yield context;
                    }
                    yield context.substring(0, index) + annotation.target() + context.substring(index + annotation.source().length());
                }
                case REGEX_FIRST ->
                    pattern.matcher(context).replaceFirst(annotation.target());
                case REGEX_ALL ->
                    pattern.matcher(context).replaceAll(annotation.target());
                case STRING_ALL ->
                    context.replace(annotation.source(), annotation.target());
            });
        }
    }

}
