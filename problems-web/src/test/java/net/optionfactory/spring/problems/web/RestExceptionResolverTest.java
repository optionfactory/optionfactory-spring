package net.optionfactory.spring.problems.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.optionfactory.spring.problems.Problem;
import java.util.List;
import net.optionfactory.spring.problems.web.RestExceptionResolver.Options;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

public class RestExceptionResolverTest {

    private RestExceptionResolver er;
    private HandlerMethod hm;

    @Before
    public void before() throws NoSuchMethodException {
        final var mapper = new ObjectMapper();
        er = new RestExceptionResolver(mapper, RestExceptionResolver.LOWEST_PRECEDENCE + 1, Options.INCLUDE_DETAILS);
        hm = new HandlerMethod(new RestExceptionResolverTest(), RestExceptionResolverTest.class.getMethod("fakeControllerMethod"));
    }

    @Test
    public void exceptionsAreResolvedWithMappingJackson2JsonView() {
        final MockHttpServletRequest req = new MockHttpServletRequest();
        final MockHttpServletResponse res = new MockHttpServletResponse();
        final Exception exception = new IllegalArgumentException();

        final ModelAndView got = er.resolveException(req, res, hm, exception);

        Assert.assertTrue(got.getView() instanceof MappingJackson2JsonView);
    }

    @Test
    public void exceptionsAreReportedAsProblemsInModel() {

        final MockHttpServletRequest req = new MockHttpServletRequest();
        final MockHttpServletResponse res = new MockHttpServletResponse();
        final Exception exception = new IllegalArgumentException();

        final ModelAndView got = er.resolveException(req, res, hm, exception);
        Object failures = got.getModel().get("errors");
        Assert.assertTrue(failures instanceof List && ((List) failures).get(0) instanceof Problem);
    }

    @ResponseBody
    public void fakeControllerMethod() {

    }

}
