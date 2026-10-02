package net.optionfactory.spring.problems.web;

import java.util.List;
import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/// Built in: the `Failure`s an application throws, answered with their own problems and `400`
/// unless the failure's class declares another status with `@ResponseStatus`.
///
/// Each problem's reason is taken as a message code and localized when the resolver's message
/// source knows it, kept as is otherwise, so a failure can be thrown with
/// `jakarta.validation.constraints.NotNull.message` as its reason. The reasons are rewritten in the
/// failure's own problems.
public class FailureProblemsModule implements ProblemsModule {

    /// @return the module's single classifier
    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(FailureProblemsModule::classify);
    }

    private static @Nullable HttpStatusAndProblems classify(ExceptionClassifier.Context context, Exception ex) {
        if (!(ex instanceof Failure failure)) {
            return null;
        }
        for (Problem problem : failure.problems) {
            problem.reason = context.localized(problem.reason, problem.reason);
        }
        return new HttpStatusAndProblems(ExceptionClassifier.annotatedStatusOr(failure, HttpStatus.BAD_REQUEST), failure.problems);
    }
}
