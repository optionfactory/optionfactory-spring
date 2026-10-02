package net.optionfactory.spring.problems.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Locale;
import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Details;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
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

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class AnnotatedNotFound extends RuntimeException {

    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class AnnotatedBug extends RuntimeException {

    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = OrderedRangeValidator.class)
    public @interface OrderedRange {

        String message() default "from must not exceed to";

        Class<?>[] groups() default {};

        Class<? extends jakarta.validation.Payload>[] payload() default {};
    }

    public static class OrderedRangeValidator implements ConstraintValidator<OrderedRange, Range> {

        @Override
        public boolean isValid(Range value, ConstraintValidatorContext context) {
            return value.from() <= value.to();
        }
    }

    @OrderedRange
    public record Range(int from, int to) {

    }

    public record Basket(@Valid List<Payload> items) {

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
    private static Resolved resolve(Exception ex, HandlerMethod handler, MockHttpServletRequest request) {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).build(new JsonMapper());
        final var res = new MockHttpServletResponse();
        final var got = er.resolveException(request, res, handler, ex);
        return new Resolved(res.getStatus(), (List<Problem>) got.getModel().get("errors"));
    }

    private static Resolved resolve(Exception ex, HandlerMethod handler) {
        return resolve(ex, handler, new MockHttpServletRequest());
    }

    private static HandlerMethod fakeHandler() throws NoSuchMethodException {
        return new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethod"));
    }

    private static MockHttpServletRequest authenticated() {
        final var request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "alice");
        return request;
    }

    private static Resolved resolve(Exception ex) throws NoSuchMethodException {
        return resolve(ex, new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethod")));
    }

    private static HttpMessageNotReadableException notReadable(Throwable cause) {
        return new HttpMessageNotReadableException("not readable", cause, new MockHttpInputMessage(new byte[0]));
    }

    @Test
    public void anUnrecognizedPropertyIsARequestErrorOnThatProperty() throws Exception {
        final var cause = Assertions.assertThrows(UnrecognizedPropertyException.class, () -> STRICT.readValue("{\"unknown\": 1}", Payload.class), "a strict mapper must reject an unknown property");
        final var got = resolve(notReadable(cause));
        Assertions.assertEquals(400, got.status(), "an unknown property is a client error");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type, "an unknown property is a problem with the request");
        Assertions.assertEquals("unknown", got.problem().context, "the problem must name the unknown property");
        Assertions.assertEquals("Campo non riconosciuto", got.problem().reason, "the reason must be localized");
    }

    @Test
    public void anUnparseableMessageIsARequestErrorWithoutContext() throws Exception {
        final var cause = Assertions.assertThrows(JacksonException.class, () -> STRICT.readValue("{not json", Payload.class), "malformed json must not parse");
        final var got = resolve(notReadable(cause));
        Assertions.assertEquals(400, got.status(), "malformed json is a client error");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type, "malformed json is a problem with the request");
        Assertions.assertNull(got.problem().context, "malformed json has no path to point at");
        Assertions.assertEquals("Impossibile analizzare il messaggio", got.problem().reason, "the reason must be localized");
    }

    @Test
    public void aValueOfTheWrongFormatIsARequestErrorOnItsPath() throws Exception {
        final var cause = Assertions.assertThrows(JacksonException.class, () -> STRICT.readValue("{\"number\": \"abc\"}", Payload.class), "a string must not deserialize as an int");
        final var got = resolve(notReadable(cause));
        Assertions.assertEquals(400, got.status(), "a value of the wrong format is a client error");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type, "a value of the wrong format is a problem with the request");
        Assertions.assertEquals("number", got.problem().context, "the problem must point at the value's path");
        Assertions.assertEquals("Formato non valido", got.problem().reason, "the reason must be localized");
    }

    @Test
    public void anUnreadableMessageWithoutACauseIsARequestError() throws Exception {
        final var got = resolve(notReadable(null));
        Assertions.assertEquals(400, got.status(), "an unreadable message is a client error");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type, "an unreadable message is a problem with the request");
        Assertions.assertEquals("Messaggio non leggibile", got.problem().reason, "the reason must be localized");
    }

    @Test
    public void aConstraintViolationIsAFieldErrorOnItsPath() throws Exception {
        try (final var factory = Validation.buildDefaultValidatorFactory()) {
            final var violations = factory.getValidator().validate(new Payload());
            final var got = resolve(new ConstraintViolationException(violations));
            Assertions.assertEquals(400, got.status(), "a constraint violation is a client error");
            Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type, "a violation on a property is a field error");
            Assertions.assertEquals("name", got.problem().context, "the problem must point at the property path");
        }
    }

    @Test
    public void aMissingParameterIsAFieldErrorOnThatParameter() throws Exception {
        final var got = resolve(new MissingServletRequestParameterException("q", "String"));
        Assertions.assertEquals(400, got.status(), "a missing parameter is a client error");
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type, "a missing parameter is a field error");
        Assertions.assertEquals("q", got.problem().context, "the problem must name the parameter");
        Assertions.assertEquals("Parametro mancante", got.problem().reason, "the reason must be localized");
    }

    @Test
    public void aMistypedArgumentIsAFieldErrorOnThatArgument() throws Exception {
        final var handler = new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethodWithParameter", int.class));
        final var ex = new MethodArgumentTypeMismatchException("abc", int.class, "id", handler.getMethodParameters()[0], new NumberFormatException());
        final var got = resolve(ex, handler);
        Assertions.assertEquals(400, got.status(), "a mistyped argument is a client error");
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type, "a mistyped argument is a field error");
        Assertions.assertEquals("id", got.problem().context, "the problem must name the argument");
        Assertions.assertEquals("Formato non valido", got.problem().reason, "the reason must be localized");
    }

    @Test
    public void aMissingPartIsAFieldErrorOnThatPart() throws Exception {
        final var got = resolve(new MissingServletRequestPartException("file"));
        Assertions.assertEquals(400, got.status(), "a missing part is a client error");
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type, "a missing part is a field error");
        Assertions.assertEquals("file", got.problem().context, "the problem must name the part");
        Assertions.assertEquals("Parametro mancante", got.problem().reason, "the reason must be localized");
    }

    @Test
    public void aResponseStatusExceptionKeepsItsStatus() throws Exception {
        final var got = resolve(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));
        Assertions.assertEquals(404, got.status(), "a ResponseStatusException must be answered with its own status");
        Assertions.assertEquals("NOT_FOUND", got.problem().type, "the problem type must be the status name");
        Assertions.assertEquals("missing", got.problem().reason, "an unknown reason code must be kept as is");
    }

    @Test
    public void aFailureIsABadRequestCarryingItsOwnProblems() throws Exception {
        final var got = resolve(Failure.field("name", "required"));
        Assertions.assertEquals(400, got.status(), "a failure is a bad request by default");
        Assertions.assertEquals(Problem.TYPE_FIELD_ERROR, got.problem().type, "the failure's own problem type must be kept");
        Assertions.assertEquals("name", got.problem().context, "the failure's own problem context must be kept");
    }

    @Test
    public void aFailureAnnotatedWithAStatusIsAnsweredWithIt() throws Exception {
        Assertions.assertEquals(409, resolve(new AnnotatedFailure()).status(), "the @ResponseStatus of the failure's class must win over 400");
    }

    @Test
    public void anUpstreamFailureIsABadGateway() throws Exception {
        final var got = resolve(new RestClientException("boom"));
        Assertions.assertEquals(502, got.status(), "an upstream failure is a bad gateway");
        Assertions.assertEquals(Problem.TYPE_UPSTREAM_ERROR, got.problem().type, "an upstream failure is an UPSTREAM_ERROR");
        Assertions.assertEquals("boom", got.problem().details, "the upstream's message must go in the details");
    }

    @Test
    public void anAccessDeniedIsForbidden() throws Exception {
        final var got = resolve(new AccessDeniedException("nope"), fakeHandler(), authenticated());
        Assertions.assertEquals(403, got.status(), "an access denied to an authenticated caller is forbidden");
        Assertions.assertEquals(Problem.TYPE_FORBIDDEN, got.problem().type, "an access denied is a FORBIDDEN problem");
        Assertions.assertEquals("nope", got.problem().details, "the exception's message must go in the details");
    }

    @Test
    public void anAccessDeniedAnnotatedWithAStatusIsAnsweredWithIt() throws Exception {
        Assertions.assertEquals(418, resolve(new AnnotatedAccessDenied(), fakeHandler(), authenticated()).status(), "the @ResponseStatus of the exception's class must win over 403");
    }

    @Test
    public void anAccessDeniedAtAnAnonymousCallerIsUnauthorized() throws Exception {
        final var got = resolve(new AccessDeniedException("nope"), fakeHandler(), new MockHttpServletRequest());
        Assertions.assertEquals(401, got.status(), "an access denied at an anonymous caller asks it to authenticate, answering 401 rather than redirecting");
        Assertions.assertEquals(Problem.TYPE_UNAUTHORIZED, got.problem().type, "an access denied at an anonymous caller is an UNAUTHORIZED problem");
    }

    @Test
    public void anAuthenticationOnTheContextHolderIsNotAnonymous() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of()));
        try {
            final var got = resolve(new AccessDeniedException("nope"), fakeHandler(), new MockHttpServletRequest());
            Assertions.assertEquals(403, got.status(), "a caller authenticated on the context holder is forbidden even without a request principal");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    public void anAnonymousAuthenticationOnTheContextHolderIsAnonymous() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        try {
            final var got = resolve(new AccessDeniedException("nope"), fakeHandler(), new MockHttpServletRequest());
            Assertions.assertEquals(401, got.status(), "spring security's anonymous authentication is an anonymous caller");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    public void aClientErrorResponseCarriesSpringsDetailAsItsReason() throws Exception {
        final var handler = new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethodWithParameter", int.class));
        final var got = resolve(new MissingRequestHeaderException("X-Required", handler.getMethodParameters()[0]), handler);
        Assertions.assertEquals(400, got.status(), "a missing header is a client error");
        Assertions.assertEquals("BAD_REQUEST", got.problem().type, "the problem type must be the status name");
        Assertions.assertTrue(got.problem().reason.contains("X-Required"), "spring's detail, naming the header, must be the reason");
    }

    @Test
    public void aServerErrorResponseKeepsSpringsDetailOutOfItsReason() throws Exception {
        final var handler = new HandlerMethod(new BuiltInCasesTest(), BuiltInCasesTest.class.getMethod("fakeControllerMethodWithParameter", int.class));
        final var got = resolve(new MissingPathVariableException("id", handler.getMethodParameters()[0]), handler);
        Assertions.assertEquals(500, got.status(), "a missing path variable is a server-side mapping error");
        Assertions.assertEquals("INTERNAL_SERVER_ERROR", got.problem().type, "the problem type must be the status name");
        Assertions.assertNull(got.problem().reason, "a server error must not expose spring's detail as its reason");
        Assertions.assertTrue(String.valueOf(got.problem().details).contains("id"), "spring's detail, naming the variable, must go in the details");
    }

    @Test
    public void anythingElseIsAnInternalServerError() throws Exception {
        final var got = resolve(new IllegalStateException("a bug"));
        Assertions.assertEquals(500, got.status(), "an unknown exception is an internal server error");
        Assertions.assertEquals(Problem.TYPE_SERVER_ERROR, got.problem().type, "an unknown exception is a SERVER_ERROR");
    }

    @Test
    public void anUnknownExceptionAnnotatedWithAStatusIsAnsweredWithIt() throws Exception {
        final var got = resolve(new AnnotatedBug());
        Assertions.assertEquals(503, got.status(), "the @ResponseStatus of an unclassified exception must win over 500");
        Assertions.assertEquals(Problem.TYPE_SERVER_ERROR, got.problem().type, "an unclassified exception is still a SERVER_ERROR");
    }

    @Test
    public void anExceptionSpringKnowsKeepsSpringsStatus() throws Exception {
        final var got = resolve(new TypeMismatchException("abc", Integer.class));
        Assertions.assertEquals(400, got.status(), "spring's DefaultHandlerExceptionResolver must pick the status of an exception it knows");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type, "an exception spring answers with a 4xx is a client error, not a SERVER_ERROR");
    }

    @Test
    public void anUnknownExceptionAnnotatedWithAClientStatusIsARequestError() throws Exception {
        final var got = resolve(new AnnotatedNotFound());
        Assertions.assertEquals(404, got.status(), "the @ResponseStatus of an unclassified exception must win over 500");
        Assertions.assertEquals(Problem.TYPE_REQUEST_ERROR, got.problem().type, "an unclassified exception annotated with a 4xx status is a client error, not a SERVER_ERROR");
    }

    @Test
    public void aClassLevelConstraintViolationIsAnObjectError() throws Exception {
        try (final var factory = Validation.buildDefaultValidatorFactory()) {
            final var violations = factory.getValidator().validate(new Range(5, 1));
            final var got = resolve(new ConstraintViolationException(violations));
            Assertions.assertEquals(Problem.TYPE_OBJECT_ERROR, got.problem().type, "a violation on the bean itself is an object error");
            Assertions.assertNull(got.problem().context, "a violation on the bean itself has no path");
        }
    }

    @Test
    public void aViolationInAListIsAFieldErrorOnItsIndex() throws Exception {
        try (final var factory = Validation.buildDefaultValidatorFactory()) {
            final var violations = factory.getValidator().validate(new Basket(List.of(new Payload(), new Payload())));
            final var contexts = resolve(new ConstraintViolationException(violations)).problems().stream().map(p -> p.context).sorted().toList();
            Assertions.assertEquals(List.of("items.0.name", "items.1.name"), contexts, "each problem must point at the element's index, dot separated");
        }
    }

    @Test
    public void aResponseStatusReasonIsLocalizedWhenItIsAMessageCode() throws Exception {
        final var got = resolve(new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.missing_parameter"));
        Assertions.assertEquals("Parametro mancante", got.problem().reason, "a reason that is a known message code must be localized");
    }

    @Test
    public void aBindingFailureReportsGlobalErrorsAsObjectErrorsAndFieldErrorsByPath() throws Exception {
        final var binding = new BeanPropertyBindingResult(new Basket(List.of()), "basket");
        binding.reject("basket.empty", "the basket is empty");
        binding.rejectValue("items", "items.missing", "items are missing");
        final var problems = resolve(new BindException(binding)).problems();
        Assertions.assertEquals(List.of(Problem.TYPE_OBJECT_ERROR, Problem.TYPE_FIELD_ERROR), problems.stream().map(p -> p.type).toList(), "global errors must come first, as object errors, then field errors");
        Assertions.assertEquals("items", problems.get(1).context, "a field error must point at its field");
        Assertions.assertEquals("the basket is empty", problems.get(0).reason, "the error's default message must be the reason");
    }

    @Test
    public void aFailureWithoutReasonIsAnsweredWithoutReason() throws Exception {
        final var got = resolve(Failure.request(null));
        Assertions.assertEquals(400, got.status(), "a failure is a bad request by default");
        Assertions.assertNull(got.problem().reason, "a problem without reason must stay without reason");
    }
}
