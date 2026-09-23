package net.optionfactory.spring.problems.web;

import java.util.List;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

/// Built in: spring security's `AccessDeniedException`, answered `403` unless its class declares
/// another status with `@ResponseStatus`.
public class SpringSecurityProblemsModule implements ProblemsModule {

    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(SpringSecurityProblemsModule::classify);
    }

    private static @Nullable HttpStatusAndProblems classify(ExceptionClassifier.Context context, Exception ex) {
        if (!(ex instanceof AccessDeniedException ade)) {
            return null;
        }
        return new HttpStatusAndProblems(ExceptionClassifier.annotatedStatusOr(ade, HttpStatus.FORBIDDEN), List.of(Problem.forbidden(null, ade.getMessage())));
    }
}
