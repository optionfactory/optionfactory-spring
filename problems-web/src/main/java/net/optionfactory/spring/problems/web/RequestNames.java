package net.optionfactory.spring.problems.web;

import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

/// The names a client sends a handler's parameters under.
final class RequestNames {

    private RequestNames() {
    }

    /// The name the client sent a parameter under: the one declared on its `@RequestParam`,
    /// `@PathVariable`, `@RequestHeader`, `@CookieValue`, `@MatrixVariable` or `@RequestPart`, else the
    /// parameter's own.
    static String of(MethodParameter param) {
        final var declared = Stream.of(
                named(param.getParameterAnnotation(RequestParam.class), RequestParam::name, RequestParam::value),
                named(param.getParameterAnnotation(PathVariable.class), PathVariable::name, PathVariable::value),
                named(param.getParameterAnnotation(RequestHeader.class), RequestHeader::name, RequestHeader::value),
                named(param.getParameterAnnotation(CookieValue.class), CookieValue::name, CookieValue::value),
                named(param.getParameterAnnotation(MatrixVariable.class), MatrixVariable::name, MatrixVariable::value),
                named(param.getParameterAnnotation(RequestPart.class), RequestPart::name, RequestPart::value)
        ).filter(name -> name != null && !name.isEmpty()).findFirst();
        return declared.orElseGet(() -> param.getParameterName() != null ? param.getParameterName() : "arg" + param.getParameterIndex());
    }

    private static <A> @Nullable String named(@Nullable A annotation, Function<A, String> name, Function<A, String> value) {
        if (annotation == null) {
            return null;
        }
        return !name.apply(annotation).isEmpty() ? name.apply(annotation) : value.apply(annotation);
    }
}
