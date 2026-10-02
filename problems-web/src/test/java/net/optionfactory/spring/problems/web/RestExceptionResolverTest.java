package net.optionfactory.spring.problems.web;

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
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.JacksonJsonView;
import tools.jackson.databind.json.JsonMapper;

public class RestExceptionResolverTest {

    @ResponseBody
    public void fakeControllerMethod() {

    }

    public String pageMethod() {
        return "page";
    }

    @RestController
    public static class FakeRestController {

        public String unannotated() {
            return "body";
        }
    }

    @BeforeEach
    public void setup() {
        LocaleContextHolder.setLocale(Locale.ITALIAN);
    }

    @AfterEach
    public void teardown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static HandlerMethod restHandler() throws NoSuchMethodException {
        return new HandlerMethod(new RestExceptionResolverTest(), RestExceptionResolverTest.class.getMethod("fakeControllerMethod"));
    }

    @SuppressWarnings("unchecked")
    private static List<Problem> problems(ModelAndView got) {
        return (List<Problem>) got.getModel().get("errors");
    }

    @Test
    public void exceptionsAreResolvedWithMappingJackson2JsonView() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).build(new JsonMapper());

        final ModelAndView got = er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), restHandler(), new IllegalArgumentException());

        Assertions.assertTrue(got.getView() instanceof JacksonJsonView, "problems must be rendered as json");
    }

    @Test
    public void exceptionsAreReportedAsProblemsInModel() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).build(new JsonMapper());

        final ModelAndView got = er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), restHandler(), new IllegalArgumentException());

        final Object failures = got.getModel().get("errors");
        Assertions.assertTrue(failures instanceof List<?> list && list.get(0) instanceof Problem, "the model must hold the list of problems under 'errors'");
    }

    @Test
    public void theResponseBodyIsTheListOfProblemsAsFailuresJson() throws Exception {
        final var er = RestExceptionResolver.builder().build(new JsonMapper());
        final var req = new MockHttpServletRequest();
        final var res = new MockHttpServletResponse();

        final var got = er.resolveException(req, res, restHandler(), Failure.field("name", "required"));
        got.getView().render(got.getModel(), req, res);

        final var contentType = MediaType.parseMediaType(res.getContentType());
        Assertions.assertEquals("application/failures+json", contentType.getType() + "/" + contentType.getSubtype(), "problems must be served as application/failures+json");
        Assertions.assertEquals("[{\"context\":\"name\",\"details\":null,\"reason\":\"required\",\"type\":\"FIELD_ERROR\"}]", res.getContentAsString(), "the body must be the bare list of problems, not wrapped in the model");
    }

    @Test
    public void detailsAreNullWhenOptionsIsOmitDetails() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.OMIT).build(new JsonMapper());
        final Exception exception = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "details");

        final ModelAndView got = er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), restHandler(), exception);

        Assertions.assertEquals(null, problems(got).get(0).details, "details must be omitted when configured so");
    }

    @Test
    public void detailsAreSerializedWhenOptionsIsIncludeDetails() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).build(new JsonMapper());
        final Exception exception = Failure.of("type", "context", "reason", "details");

        final ModelAndView got = er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), restHandler(), exception);

        Assertions.assertEquals("details", problems(got).get(0).details, "details must be kept when configured so");
    }

    @Test
    public void responseStatusExceptionWithNonStandardStatusCodeIsHandledWithoutNpe() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().withDetails(Details.INCLUDE).build(new JsonMapper());
        final var res = new MockHttpServletResponse();
        final Exception exception = new ResponseStatusException(HttpStatusCode.valueOf(599), "custom");

        final ModelAndView got = er.resolveException(new MockHttpServletRequest(), res, restHandler(), exception);

        Assertions.assertNotNull(got, "a status HttpStatus does not know, such as 599, must still be answered");
        Assertions.assertEquals(599, res.getStatus(), "the non-standard status must be kept");
        Assertions.assertEquals("HTTP_599", problems(got).get(0).type, "a status without a name must be typed by its number");
    }

    @Test
    public void aHandlerThatIsNotResponseBodyIsDeclined() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().build(new JsonMapper());
        final var page = new HandlerMethod(new RestExceptionResolverTest(), RestExceptionResolverTest.class.getMethod("pageMethod"));

        Assertions.assertNull(er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), page, new IllegalStateException()), "a page handler must be left to the resolvers behind");
    }

    @Test
    public void aHandlerThatIsNotAHandlerMethodIsDeclined() {
        final var er = RestExceptionResolver.builder().build(new JsonMapper());

        Assertions.assertNull(er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), new IllegalStateException()), "only handler methods can be rest handlers");
    }

    @Test
    public void aRestControllerMethodIsAnsweredWithoutItsOwnAnnotation() throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().build(new JsonMapper());
        final var handler = new HandlerMethod(new FakeRestController(), FakeRestController.class.getMethod("unannotated"));

        Assertions.assertNotNull(er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), handler, new IllegalStateException()), "@ResponseBody on the class, through @RestController, must be enough");
    }

    @Test
    public void theApplicationsMessagesWinAndTheBuiltInOnesFillTheGaps() throws NoSuchMethodException {
        final var messages = new StaticMessageSource();
        messages.addMessage("error.custom", Locale.ITALIAN, "messaggio dell'applicazione");
        messages.addMessage("error.invalid_format", Locale.ITALIAN, "formato sbagliato");
        final var er = RestExceptionResolver.builder().withMessageSource(messages).build(new JsonMapper());
        final var failure = Failure.builder().field("a", "error.custom").field("b", "error.invalid_format").field("c", "error.missing_parameter").build();

        final var reasons = problems(er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), restHandler(), failure)).stream().map(p -> p.reason).toList();

        Assertions.assertEquals(List.of("messaggio dell'applicazione", "formato sbagliato", "Parametro mancante"), reasons, "the application's messages must win, the built-in ones answering the codes it does not know");
    }

    @Test
    public void aResolverBuiltWithItsConstructorHasNoBuiltInClassifiers() throws NoSuchMethodException {
        final var er = new RestExceptionResolver(new JsonMapper(), new StaticMessageSource(), List.of(), List.of());
        final var res = new MockHttpServletResponse();

        er.resolveException(new MockHttpServletRequest(), res, restHandler(), Failure.field("name", "required"));

        Assertions.assertEquals(500, res.getStatus(), "without the built-in classifiers, a failure is an unexpected error");
    }

    @Test
    public void classifyingWithoutAnAnnotatedStatusFallsBack() {
        Assertions.assertEquals(HttpStatus.CONFLICT, ExceptionClassifier.annotatedStatusOr(null, HttpStatus.CONFLICT), "a null exception must yield the fallback");
        Assertions.assertEquals(HttpStatus.CONFLICT, ExceptionClassifier.annotatedStatusOr(new IllegalStateException(), HttpStatus.CONFLICT), "an exception class without @ResponseStatus must yield the fallback");
    }
}
