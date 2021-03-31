package net.optionfactory.spring.problems.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.optionfactory.spring.problems.Problem;
import java.util.List;
import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Options;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

public class RestExceptionResolverTest {

    @ResponseBody
    public void fakeControllerMethod() {

    }

    @Test
    public void exceptionsAreResolvedWithMappingJackson2JsonView() throws NoSuchMethodException {
        final var mapper = new ObjectMapper();
        final var er = new RestExceptionResolver(mapper, Options.INCLUDE_DETAILS);
        final var hm = new HandlerMethod(new RestExceptionResolverTest(), RestExceptionResolverTest.class.getMethod("fakeControllerMethod"));
        final MockHttpServletRequest req = new MockHttpServletRequest();
        final MockHttpServletResponse res = new MockHttpServletResponse();
        final Exception exception = new IllegalArgumentException();

        final ModelAndView got = er.resolveException(req, res, hm, exception);

        Assert.assertTrue(got.getView() instanceof MappingJackson2JsonView);
    }

    @Test
    public void exceptionsAreReportedAsProblemsInModel() throws NoSuchMethodException {
        final var mapper = new ObjectMapper();
        final var er = new RestExceptionResolver(mapper, Options.INCLUDE_DETAILS);
        final var hm = new HandlerMethod(new RestExceptionResolverTest(), RestExceptionResolverTest.class.getMethod("fakeControllerMethod"));

        final MockHttpServletRequest req = new MockHttpServletRequest();
        final MockHttpServletResponse res = new MockHttpServletResponse();
        final Exception exception = new IllegalArgumentException();

        final ModelAndView got = er.resolveException(req, res, hm, exception);
        Object failures = got.getModel().get("errors");
        Assert.assertTrue(failures instanceof List && ((List) failures).get(0) instanceof Problem);
    }

    @Test
    public void detailsAreNullWhenOptionsIsOmitDetails() throws NoSuchMethodException {
        final var mapper = new ObjectMapper();
        final RestExceptionResolver er = new RestExceptionResolver(mapper, Options.OMIT_DETAILS);
        final HandlerMethod hm = new HandlerMethod(new RestExceptionResolverTest(), RestExceptionResolverTest.class.getMethod("fakeControllerMethod"));

        final MockHttpServletRequest req = new MockHttpServletRequest();
        final MockHttpServletResponse res = new MockHttpServletResponse();
        final Exception exception = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "details");

        final ModelAndView got = er.resolveException(req, res, hm, exception);
        final Object failures = got.getModel().get("errors");
        final Problem problem = (Problem) ((List) failures).get(0);
        Assert.assertEquals(null, problem.details);
    }

    @Test
    public void detailsAreSerializedWhenOptionsIsIncludeDetails() throws NoSuchMethodException {
        final var mapper = new ObjectMapper();
        final RestExceptionResolver er = new RestExceptionResolver(mapper, Options.INCLUDE_DETAILS);
        final HandlerMethod hm = new HandlerMethod(new RestExceptionResolverTest(), RestExceptionResolverTest.class.getMethod("fakeControllerMethod"));

        final MockHttpServletRequest req = new MockHttpServletRequest();
        final MockHttpServletResponse res = new MockHttpServletResponse();
        final Exception exception = new Failure(Problem.of("type", "context", "reason", "details"));

        final ModelAndView got = er.resolveException(req, res, hm, exception);
        final Object failures = got.getModel().get("errors");
        final Problem problem = (Problem) ((List) failures).get(0);
        Assert.assertEquals("details", problem.details);
    }

}
