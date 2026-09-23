package net.optionfactory.spring.problems.web;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Details;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/// Pins how each of the resolver's built-in cases is answered: status, problem type, context and
/// reason. Reasons are localized, so the locale is fixed to one the bundles ship.
public class BuiltInCasesTest {

    @ResponseBody
    public void fakeControllerMethod() {

    }

    @ResponseBody
    public void fakeControllerMethodWithParameter(int id) {

    }

    public static class Payload {

        public int number;
        @NotNull
        public String name;
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class AnnotatedFailure extends Failure {

        public AnnotatedFailure() {
            super(List.of(Problem.of("CONFLICT", null, "conflict", null)), null, null);
        }
    }

    @ResponseStatus(HttpStatus.I_AM_A_TEAPOT)
    public static class AnnotatedAccessDenied extends AccessDeniedException {

        public AnnotatedAccessDenied() {
            super("teapot");
        }
    }

    private record Resolved(int status, List<Problem> problems) {

        Problem problem() {
            return problems.get(0);
        }
    }

    private static final JsonMapper STRICT = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @BeforeEach
    public void setup() {
        LocaleContextHolder.setLocale(Locale.ITALIAN);
    }

    @AfterEach
    public void teardown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @SuppressWarnings("unchecked")
    private static Resolved resolve(Exception ex, HandlerMethod handler) {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).build(new JsonMapper());
        final var res = new MockHttpServletResponse();
        final var got = er.resolveException(new MockHttpServletRequest(), res, handler, ex);
        return new Resolved(res.getStatus(), (List<Problem>) got.getModel().get("errors"));
    }

    private static Resolved resolve(Exception ex) throws NoSuchMethodException {
        return resolve(ex, new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethod")));
    }

    private static HttpMessageNotReadableException notReadable(Throwable cause) {
        return new HttpMessageNotReadableException("not readable", cause, new MockHttpInputMessage(new byte[0]));
    }

    @Test
    public void anUnrecognizedPropertyIsARequestErrorOnThatProperty() throws Exception {
        final var cause = Assertions.assertThrows(UnrecognizedPropertyException.class, () -> STRICT.readValue("{\"unknown\": 1}", Payload.class));
        final var got = resolve(notReadable(cause));
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type);
        Assertions.assertEquals("unknown", got.problem().context);
        Assertions.assertEquals("Campo non riconosciuto", got.problem().reason);
    }

    @Test
    public void anUnparseableMessageIsARequestErrorWithoutContext() throws Exception {
        final var cause = Assertions.assertThrows(JacksonException.class, () -> STRICT.readValue("{not json", Payload.class));
        final var got = resolve(notReadable(cause));
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type);
        Assertions.assertNull(got.problem().context);
        Assertions.assertEquals("Impossibile analizzare il messaggio", got.problem().reason);
    }

    @Test
    public void aValueOfTheWrongFormatIsARequestErrorOnItsPath() throws Exception {
        final var cause = Assertions.assertThrows(JacksonException.class, () -> STRICT.readValue("{\"number\": \"abc\"}", Payload.class));
        final var got = resolve(notReadable(cause));
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type);
        Assertions.assertEquals("number", got.problem().context);
        Assertions.assertEquals("Formato non valido", got.problem().reason);
    }

    @Test
    public void anUnreadableMessageWithoutACauseIsARequestError() throws Exception {
        final var got = resolve(notReadable(null));
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type);
        Assertions.assertEquals("Messaggio non leggibile", got.problem().reason);
    }

    @Test
    public void aConstraintViolationIsAFieldErrorOnItsPath() throws Exception {
        try (final var factory = Validation.buildDefaultValidatorFactory()) {
            final var violations = factory.getValidator().validate(new Payload());
            final var got = resolve(new ConstraintViolationException(violations));
            Assertions.assertEquals(400, got.status());
            Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type);
            Assertions.assertEquals("name", got.problem().context);
        }
    }

    @Test
    public void aMissingParameterIsAFieldErrorOnThatParameter() throws Exception {
        final var got = resolve(new MissingServletRequestParameterException("q", "String"));
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type);
        Assertions.assertEquals("q", got.problem().context);
        Assertions.assertEquals("Parametro mancante", got.problem().reason);
    }

    @Test
    public void aMistypedArgumentIsAFieldErrorOnThatArgument() throws Exception {
        final var handler = new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethodWithParameter", int.class));
        final var ex = new MethodArgumentTypeMismatchException("abc", int.class, "id", handler.getMethodParameters()[0], new NumberFormatException());
        final var got = resolve(ex, handler);
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type);
        Assertions.assertEquals("id", got.problem().context);
        Assertions.assertEquals("Formato non valido", got.problem().reason);
    }

    @Test
    public void aMissingPartIsAFieldErrorOnThatPart() throws Exception {
        final var got = resolve(new MissingServletRequestPartException("file"));
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type);
        Assertions.assertEquals("file", got.problem().context);
        Assertions.assertEquals("Parametro mancante", got.problem().reason);
    }

    @Test
    public void aResponseStatusExceptionKeepsItsStatus() throws Exception {
        final var got = resolve(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));
        Assertions.assertEquals(404, got.status());
        Assertions.assertEquals("NOT_FOUND", got.problem().type);
        Assertions.assertEquals("missing", got.problem().reason);
    }

    @Test
    public void aFailureIsABadRequestCarryingItsOwnProblems() throws Exception {
        final var got = resolve(Failure.field("name", "required"));
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type);
        Assertions.assertEquals("name", got.problem().context);
    }

    @Test
    public void aFailureAnnotatedWithAStatusIsAnsweredWithIt() throws Exception {
        Assertions.assertEquals(409, resolve(new AnnotatedFailure()).status());
    }

    @Test
    public void anUpstreamFailureIsABadGateway() throws Exception {
        final var got = resolve(new RestClientException("boom"));
        Assertions.assertEquals(502, got.status());
        Assertions.assertEquals(Problem.TYPE_UPSTREAM_ERROR, got.problem().type);
        Assertions.assertEquals("boom", got.problem().details);
    }

    @Test
    public void anAccessDeniedIsForbidden() throws Exception {
        final var got = resolve(new AccessDeniedException("nope"));
        Assertions.assertEquals(403, got.status());
        Assertions.assertEquals(Problem.TYPE_FORBIDDEN, got.problem().type);
        Assertions.assertEquals("nope", got.problem().details);
    }

    @Test
    public void anAccessDeniedAnnotatedWithAStatusIsAnsweredWithIt() throws Exception {
        Assertions.assertEquals(418, resolve(new AnnotatedAccessDenied()).status());
    }

    @Test
    public void aClientErrorResponseCarriesSpringsDetailAsItsReason() throws Exception {
        final var handler = new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethodWithParameter", int.class));
        final var got = resolve(new MissingRequestHeaderException("X-Required", handler.getMethodParameters()[0]), handler);
        Assertions.assertEquals(400, got.status());
        Assertions.assertEquals("BAD_REQUEST", got.problem().type);
        Assertions.assertTrue(got.problem().reason.contains("X-Required"), got.problem().reason);
    }

    @Test
    public void aServerErrorResponseKeepsSpringsDetailOutOfItsReason() throws Exception {
        final var handler = new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethodWithParameter", int.class));
        final var got = resolve(new MissingPathVariableException("id", handler.getMethodParameters()[0]), handler);
        Assertions.assertEquals(500, got.status());
        Assertions.assertEquals("INTERNAL_SERVER_ERROR", got.problem().type);
        Assertions.assertNull(got.problem().reason);
        Assertions.assertTrue(String.valueOf(got.problem().details).contains("id"), String.valueOf(got.problem().details));
    }

    @Test
    public void anythingElseIsAnInternalServerError() throws Exception {
        final var got = resolve(new IllegalStateException("a bug"));
        Assertions.assertEquals(500, got.status());
        Assertions.assertEquals(Problem.TYPE_SERVER_ERROR, got.problem().type);
    }
}
