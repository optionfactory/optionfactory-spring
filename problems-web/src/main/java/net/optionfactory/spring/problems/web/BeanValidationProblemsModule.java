package net.optionfactory.spring.problems.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path.Node;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;

/// Built in: a jakarta bean validation `ConstraintViolationException`, as thrown by method validation
/// outside spring mvc's own.
public class BeanValidationProblemsModule implements ProblemsModule {

    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(BeanValidationProblemsModule::classify);
    }

    private static @Nullable HttpStatusAndProblems classify(ExceptionClassifier.Context context, Exception ex) {
        if (!(ex instanceof ConstraintViolationException cve)) {
            return null;
        }
        final var hm = context.handler();
        final var requestBodyParams = hm == null ? Set.<String>of() : Stream.of(hm.getMethodParameters())
                .filter(p -> p.hasParameterAnnotation(RequestBody.class))
                .map(p -> p.getParameterName() != null ? p.getParameterName() : "arg" + p.getParameterIndex())
                .collect(Collectors.toSet());
        final var failures = cve.getConstraintViolations().stream()
                .map(cv -> constraintViolationToProblem(cv, requestBodyParams))
                .toList();
        return new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, failures);
    }

    private static Problem constraintViolationToProblem(ConstraintViolation<?> error, Set<String> requestBodyParams) {
        final var nodes = StreamSupport.stream(error.getPropertyPath().spliterator(), false)
                .toList();

        final var paramName = nodes.stream()
                .filter(node -> node.getKind() == ElementKind.PARAMETER)
                .map(Node::getName)
                .findFirst()
                .orElse(null);

        final var isRequestBody = paramName != null && requestBodyParams.contains(paramName);

        final var path = nodes.stream()
                .filter(node -> node.getKind() != ElementKind.METHOD)
                .filter(node -> !(node.getKind() == ElementKind.PARAMETER && isRequestBody))
                .map(node -> {
                    String name = node.getName();
                    if (name != null && name.startsWith("<") && name.endsWith(">")) {
                        name = "";
                    }

                    if (node.getIndex() != null) {
                        return node.getIndex() + (name != null && !name.isEmpty() ? "." + name : "");
                    }
                    return name;
                })
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("."));

        return path.isEmpty()
                ? Problem.of(Problem.TYPE_OBJECT_ERROR, null, error.getMessage(), null)
                : Problem.of(Problem.TYPE_FIELD_ERROR, path, error.getMessage(), null);
    }
}
