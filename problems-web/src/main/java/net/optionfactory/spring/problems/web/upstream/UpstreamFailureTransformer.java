package net.optionfactory.spring.problems.web.upstream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.FailureTransformer;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import net.optionfactory.spring.upstream.errors.RestClientUpstreamException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.method.HandlerMethod;

public class UpstreamFailureTransformer implements FailureTransformer {

    private static final ParameterizedTypeReference<List<Problem>> PROBLEMS_LIST = new ParameterizedTypeReference<>() {
    };
    private final Map<HandlerMethod, Optional<UpstreamProblems.MapContext[]>> drops = new ConcurrentHashMap<>();
    private final Map<HandlerMethod, Optional<UpstreamProblems.Forward>> statuses = new ConcurrentHashMap<>();

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
        final var maybeAnnotation = drops.computeIfAbsent(handler, (hm) -> Optional.ofNullable(hm.getMethod().getAnnotationsByType(UpstreamProblems.MapContext.class)));
        if (maybeAnnotation.isEmpty()) {
            return saps;
        }
        final var annotations = maybeAnnotation.get();
        for (final var annotation : annotations) {
            if (!annotation.upstream().isEmpty() && !annotation.upstream().equals(uex.upstream)) {
                continue;
            }
            if (!annotation.endpoint().isEmpty() && !annotation.endpoint().equals(uex.endpoint)) {
                continue;
            }
            for (Problem problem : saps.problems()) {
                if (problem.context == null) {
                    continue;
                }
                problem.context = switch (annotation.mode()) {
                    case STRING_FIRST -> {
                        int index = problem.context.indexOf(annotation.source());
                        if (index < 0) {
                            yield problem.context; // search string not found
                        }
                        yield problem.context.substring(0, index) + annotation.target() + problem.context.substring(index + annotation.source().length());
                    }
                    case REGEX_FIRST ->
                        problem.context.replaceFirst(annotation.source(), annotation.target());
                    case REGEX_ALL ->
                        problem.context.replaceAll(annotation.source(), annotation.target());
                    case STRING_ALL ->
                        problem.context.replace(annotation.source(), annotation.target());
                };
            }

        }
        return saps;
    }

}
