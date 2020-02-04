package net.optionfactory.problems.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import net.optionfactory.problems.Problem;
import java.util.List;
import java.util.function.Supplier;
import net.optionfactory.problems.web.RestExceptionResolver.Options;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

/**
 *
 * @author rferranti
 */
public class RestExceptionResolverTest {

    private RestExceptionResolver er;
    private HandlerMethod hm;

    @Before
    public void before() throws NoSuchMethodException {
        final ContentNegotiationManager cnm = new ContentNegotiationManager();
        final LinkedHashMap<MediaType, Supplier<View>> suppliers = new LinkedHashMap<>();
        suppliers.put(MediaType.APPLICATION_JSON, new JsonViewFactory(new ObjectMapper()));
        er = new RestExceptionResolver(cnm, suppliers, RestExceptionResolver.LOWEST_PRECEDENCE + 1, Options.INCLUDE_DETAILS);
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
