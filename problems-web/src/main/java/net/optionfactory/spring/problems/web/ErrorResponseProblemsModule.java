package net.optionfactory.spring.problems.web;

import java.util.List;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;

/// Built in: spring's remaining standard exceptions, those implementing `ErrorResponse` — an
/// unsupported request body type, a response type the client does not accept, a missing request
/// header — answered with their own status, headers and localized detail. Consulted after every
/// other built-in module, as the most general of them.
public class ErrorResponseProblemsModule implements ProblemsModule {

    private static final Logger logger = LoggerFactory.getLogger(ErrorResponseProblemsModule.class);

    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(ErrorResponseProblemsModule::classify);
    }

    private static @Nullable HttpStatusAndProblems classify(ExceptionClassifier.Context context, Exception ex) {
        if (!(ex instanceof ErrorResponse er)) {
            return null;
        }
        final var status = er.getStatusCode();
        er.getHeaders().forEach((name, values) -> values.forEach(value -> context.response().addHeader(name, value)));
        final var resolved = HttpStatus.resolve(status.value());
        final var type = resolved != null ? resolved.name() : String.format("HTTP_%d", status.value());
        final var reason = context.messages().getMessage(er.getDetailMessageCode(), er.getDetailMessageArguments(), er.getBody().getDetail(), context.locale());
        if (status.is5xxServerError()) {
            logger.warn(String.format("server error at %s: %s", context.request().getRequestURI(), ex.getMessage()), ex);
        }
        return new HttpStatusAndProblems(status, List.of(Problem.of(type, null, reason, Problem.NO_DETAILS)));
    }
}
