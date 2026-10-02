package net.optionfactory.spring.problems.web;

import java.util.List;
import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Details;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.json.JsonMapper;

public class ExceptionClassifierTest {

    @ResponseBody
    public void fakeControllerMethod() {

    }

    public static class LibraryRejection extends RuntimeException {

    }

    private static final ExceptionClassifier LIBRARY_CLASSIFIER = (context, ex) -> ex instanceof LibraryRejection
            ? new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.request("byName", "rejected", "internal detail")))
            : null;

    private static final ExceptionClassifier DECLINING_CLASSIFIER = (context, ex) -> null;

    private static final ExceptionClassifier BROKEN_CLASSIFIER = (context, ex) -> {
        throw new NullPointerException("bug in a library classifier");
    };

    private static final ExceptionClassifier RETHROWING_CLASSIFIER = (context, ex) -> {
        throw (RuntimeException) ex;
    };

    public static class PaymentRequired extends Failure {

        public PaymentRequired() {
            super(List.of(Problem.request("pay first")), null, null);
        }
    }

    private static final ExceptionClassifier PAYMENT_CLASSIFIER = (context, ex) -> ex instanceof PaymentRequired pr
            ? new HttpStatusAndProblems(HttpStatus.PAYMENT_REQUIRED, pr.problems)
            : null;

    private static HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new ExceptionClassifierTest(), ExceptionClassifierTest.class.getMethod("fakeControllerMethod"));
    }

    @SuppressWarnings("unchecked")
    private static List<Problem> problems(ModelAndView got) {
        return (List<Problem>) got.getModel().get("errors");
    }

    @Test
    public void aClassifierAnswersAnExceptionTheResolverDoesNotKnow() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).withClassifier(LIBRARY_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        final var got = er.resolveException(new MockHttpServletRequest(), res, handler(), new LibraryRejection());

        Assertions.assertEquals(400, res.getStatus(), "the classifier's status must be answered");
        Assertions.assertEquals("byName", problems(got).get(0).context, "the classifier's problems must be answered");
        Assertions.assertEquals("rejected", problems(got).get(0).reason, "the classifier's reason must be answered");
    }

    @Test
    public void withoutAClassifierTheSameExceptionIsAnUnexpectedError() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        er.resolveException(new MockHttpServletRequest(), res, handler(), new LibraryRejection());

        Assertions.assertEquals(500, res.getStatus(), "an exception nobody classifies is an internal server error");
    }

    @Test
    public void classifiersAreConsultedInRegistrationOrderUntilOneClaimsTheException() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withClassifier(DECLINING_CLASSIFIER).withClassifier(LIBRARY_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        er.resolveException(new MockHttpServletRequest(), res, handler(), new LibraryRejection());

        Assertions.assertEquals(400, res.getStatus(), "a declining classifier must hand the exception to the next");
    }

    @Test
    public void anExceptionEveryClassifierDeclinesFallsBackToTheDefaults() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withClassifier(DECLINING_CLASSIFIER).withClassifier(LIBRARY_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        er.resolveException(new MockHttpServletRequest(), res, handler(), new IllegalStateException("a bug"));

        Assertions.assertEquals(500, res.getStatus(), "an exception every classifier declines must fall back to the defaults");
    }

    @Test
    public void aClassifierCanRefineABuiltInCase() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withClassifier(PAYMENT_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        er.resolveException(new MockHttpServletRequest(), res, handler(), new PaymentRequired());

        Assertions.assertEquals(402, res.getStatus(), "a registered classifier must be consulted before the built-in Failure case");
    }

    @Test
    public void aBuiltInCaseStillAnswersWhatNoClassifierClaims() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withClassifier(PAYMENT_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        final var got = er.resolveException(new MockHttpServletRequest(), res, handler(), Failure.field("name", "required"));

        Assertions.assertEquals(400, res.getStatus(), "the built-in Failure case must answer what the classifier declines");
        Assertions.assertEquals("name", problems(got).get(0).context, "the failure's own problems must be answered");
    }

    @Test
    public void aThrowingClassifierIsSkippedInFavourOfTheNext() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withClassifier(BROKEN_CLASSIFIER).withClassifier(LIBRARY_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        er.resolveException(new MockHttpServletRequest(), res, handler(), new LibraryRejection());

        Assertions.assertEquals(400, res.getStatus(), "the classifier after a throwing one must still answer");
    }

    @Test
    public void aThrowingClassifierDoesNotHideTheExceptionItWasOffered() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).withClassifier(BROKEN_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        final var got = er.resolveException(new MockHttpServletRequest(), res, handler(), new IllegalStateException("the real error"));

        Assertions.assertEquals(500, res.getStatus(), "the original exception must be answered as unexpected");
        Assertions.assertEquals("the real error", problems(got).get(0).details, "the details must be the original exception's, not the classifier's");
    }

    @Test
    public void aClassifierRethrowingTheExceptionItWasOfferedIsSkippedToo() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withClassifier(RETHROWING_CLASSIFIER).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        er.resolveException(new MockHttpServletRequest(), res, handler(), Failure.field("name", "required"));

        Assertions.assertEquals(400, res.getStatus(), "a classifier rethrowing the exception must be skipped like any throwing one");
    }

    @Test
    public void aClassifierLocalizesThroughTheContext() throws NoSuchMethodException {
        final var messages = new StaticMessageSource();
        messages.addMessage("error.rejected", LocaleContextHolder.getLocale(), "localized rejection");
        final ExceptionClassifier localizing = (context, ex) -> new HttpStatusAndProblems(HttpStatus.BAD_REQUEST, List.of(Problem.request(context.localized("error.rejected", "fallback"))));
        final var er = RestExceptionResolver.builder().withMessageSource(messages).withClassifier(localizing).build(new JsonMapper());

        final var got = er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), handler(), new LibraryRejection());

        Assertions.assertEquals("localized rejection", problems(got).get(0).reason, "the context must localize with the configured message source");
    }

    @Test
    public void aModuleRegistersItsClassifiersAndTransformersInOneGo() throws NoSuchMethodException {
        final var module = new ProblemsModule() {
            @Override
            public List<ExceptionClassifier> classifiers() {
                return List.of(LIBRARY_CLASSIFIER);
            }

            @Override
            public List<FailureTransformer> transformers() {
                return List.of((saps, request, response, handler, ex) -> new HttpStatusAndProblems(HttpStatus.UNPROCESSABLE_CONTENT, saps.problems()));
            }
        };
        final var er = RestExceptionResolver.builder().withModule(module).build(new JsonMapper());
        final var res = new MockHttpServletResponse();

        final var got = er.resolveException(new MockHttpServletRequest(), res, handler(), new LibraryRejection());

        Assertions.assertEquals(422, res.getStatus(), "the module's transformer must run");
        Assertions.assertEquals("byName", problems(got).get(0).context, "the module's classifier must run");
    }

    @Test
    public void aModuleCannotIncludeDetailsInProduction() throws NoSuchMethodException {
        final var leaking = new ProblemsModule() {
            @Override
            public List<ExceptionClassifier> classifiers() {
                return List.of(LIBRARY_CLASSIFIER);
            }

            @Override
            public List<FailureTransformer> transformers() {
                return List.of((saps, request, response, handler, ex) -> {
                    saps.problems().forEach(p -> p.details = "internal state");
                    return saps;
                });
            }
        };
        final var er = RestExceptionResolver.builder().withDetails(Details.OMIT).withModule(leaking).build(new JsonMapper());

        final var got = er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), handler(), new LibraryRejection());

        Assertions.assertNull(problems(got).get(0).details, "details must be omitted after every transformer, a module's included");
    }

    @Test
    public void detailsOfAClassifiedFailureAreStillOmittedInProduction() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.OMIT).withClassifier(LIBRARY_CLASSIFIER).build(new JsonMapper());

        final var got = er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), handler(), new LibraryRejection());

        Assertions.assertNull(problems(got).get(0).details, "the details of a classified exception must still be omitted");
    }
}
