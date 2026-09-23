package net.optionfactory.spring.problems.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path.MethodNode;
import jakarta.validation.Path.Node;
import jakarta.validation.Path.ParameterNode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

/// Built in: a jakarta bean validation `ConstraintViolationException`, as thrown by the old style of
/// method validation.
///
/// Spring validates a controller method's parameters in one of two styles, and each is answered by a
/// different module:
///
/// - **New style, spring 6.1 and later.** Constraints are declared straight on the handler's
///   parameters — `@RequestParam("q") @Min(1) int query` — with no `@Validated` on the class. Spring
///   mvc validates them itself while resolving the arguments and throws a
///   `HandlerMethodValidationException`, answered by [SpringWebProblemsModule] with a problem per
///   violated parameter, named as the client sent it. A `@Valid` request body or model attribute fails
///   with a `MethodArgumentNotValidException`, answered there too.
/// - **Old style.** The controller class is annotated with `@Validated` and a
///   `MethodValidationPostProcessor` is registered: an AOP proxy validates each call and throws the
///   `ConstraintViolationException` answered here. A class-level `@Validated` switches spring mvc's own
///   validation off for that controller, so without the post processor its constraints are not checked
///   at all — the handler runs with whatever was sent.
///
/// A violation of the handler's own parameters is named as the client sent them, like the new style's.
/// The same exception is thrown by any other `@Validated` bean, a service the handler calls for
/// instance, and a violation there is not about the request: its context is its property path, named
/// after the java parameters and properties it runs through.
public class BeanValidationProblemsModule implements ProblemsModule {

    @Override
    public List<ExceptionClassifier> classifiers() {
        return List.of(BeanValidationProblemsModule::classify);
    }

    private static @Nullable HttpStatusAndProblems classify(ExceptionClassifier.Context context, Exception ex) {
        if (!(ex instanceof ConstraintViolationException cve)) {
            return null;
        }
        final var failures = cve.getConstraintViolations().stream()
                .map(cv -> constraintViolationToProblem(cv, context.handler()))
                .toList();
        return new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, failures);
    }

    private static Problem constraintViolationToProblem(ConstraintViolation<?> error, @Nullable HandlerMethod handler) {
        final var nodes = StreamSupport.stream(error.getPropertyPath().spliterator(), false)
                .toList();
        final var handlerParameters = handler != null && violatesHandler(error, nodes, handler) ? handler.getMethodParameters() : null;

        final var path = nodes.stream()
                .filter(node -> node.getKind() != ElementKind.METHOD)
                .map(node -> {
                    if (node.getKind() == ElementKind.PARAMETER && handlerParameters != null) {
                        final var param = handlerParameters[node.as(ParameterNode.class).getParameterIndex()];
                        return param.hasParameterAnnotation(RequestBody.class) ? "" : RequestNames.of(param);
                    }
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

    /// Whether the violation is on the handler method's own parameters, rather than on a bean the
    /// handler called: only then do its parameter nodes stand for what the client sent.
    private static boolean violatesHandler(ConstraintViolation<?> error, List<Node> nodes, HandlerMethod handler) {
        final var method = handler.getMethod();
        return handler.getBeanType().isAssignableFrom(error.getRootBeanClass()) && nodes.stream()
                .filter(node -> node.getKind() == ElementKind.METHOD)
                .map(node -> node.as(MethodNode.class))
                .anyMatch(node -> node.getName().equals(method.getName()) && node.getParameterTypes().equals(List.of(method.getParameterTypes())));
    }
}
