package net.optionfactory.spring.problems.web.datajpa;

import java.util.List;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidFilterRequest;
import net.optionfactory.spring.data.jpa.filtering.filters.spi.InvalidSortRequest;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.ExceptionClassifier;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/// Answers a filter or sort request the repository rejected as what it is: a bad request.
///
/// Without it, `problems-web` sees these as unexpected errors. Both rejections extend
/// `IllegalArgumentException`, which spring's JPA exception translation rewraps as an
/// `InvalidDataAccessApiUsageException` on its way out of the repository, and the resolver has no
/// case for that: it logs a stack trace at `ERROR` and answers `500`. This classifier finds the
/// rejection in the cause chain and answers `400` with a `FIELD_ERROR` problem instead, logged at
/// `DEBUG` like any other client error. A field error, like the resolver's own for a missing or
/// mistyped request parameter: the problem points at one named input the client controls.
///
/// The problem's `context` is the filter or sorter name and its `reason` is phrased in terms of the
/// client's request, so neither reveals the entity behind the name. The full message, which does
/// name the entity, goes in `details`, which the resolver omits in production.
///
/// Registered through [DataJpaProblemsModule].
public class FilteringExceptionClassifier implements ExceptionClassifier {

    @Override
    public @Nullable HttpStatusAndProblems classify(Context context, Exception ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof InvalidFilterRequest ifr) {
                return badRequest(ifr.filter, ifr.reason, ifr.getMessage());
            }
            if (t instanceof InvalidSortRequest isr) {
                return badRequest(isr.sorter, isr.reason, isr.getMessage());
            }
        }
        return null;
    }

    private static HttpStatusAndProblems badRequest(String context, String reason, String details) {
        return new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.field(context, reason, details)));
    }
}
