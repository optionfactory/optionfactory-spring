package net.optionfactory.spring.problems.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

public class PagesExceptionResolverTest {

    public static class NotFound extends RuntimeException {

    }

    public static class SpecificNotFound extends NotFound {

    }

    private static final PagesExceptionResolver DEFAULTS = PagesExceptionResolver.builder().build();

    @Test
    public void anAccessDeniedIsLeftToSpringSecurity() {
        final var mapped = PagesExceptionResolver.builder().with(RuntimeException.class, "errors/runtime", 418).build();
        final var got = mapped.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), null, new AccessDeniedException("nope"));
        Assertions.assertNull(got, "an AccessDeniedException must be declined even when a mapping would match it");
    }

    @Test
    public void anUnmappedExceptionIsAnInternalServerErrorOnTheErrorView() {
        final var res = new MockHttpServletResponse();
        final var got = DEFAULTS.resolveException(new MockHttpServletRequest(), res, null, new IllegalStateException("a bug"));
        Assertions.assertEquals("error", got.getViewName(), "the default view must be 'error'");
        Assertions.assertEquals(500, res.getStatus(), "an unexpected exception must be answered 500");
    }

    @Test
    public void aResponseStatusExceptionKeepsItsStatus() {
        final var res = new MockHttpServletResponse();
        DEFAULTS.resolveException(new MockHttpServletRequest(), res, null, new ResponseStatusException(HttpStatus.NOT_FOUND));
        Assertions.assertEquals(404, res.getStatus(), "a ResponseStatusException must be answered with its own status");
    }

    @Test
    public void anUpstreamFailureIsABadGateway() {
        final var res = new MockHttpServletResponse();
        DEFAULTS.resolveException(new MockHttpServletRequest(), res, null, new RestClientException("down"));
        Assertions.assertEquals(502, res.getStatus(), "a RestClientException must be answered 502");
    }

    @Test
    public void theDefaultViewCanBeChanged() {
        final var resolver = PagesExceptionResolver.builder().with("errors/generic").build();
        final var got = resolver.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), null, new IllegalStateException("a bug"));
        Assertions.assertEquals("errors/generic", got.getViewName(), "an unmapped exception must render the configured default view");
    }

    @Test
    public void aMappingAnswersSubclassesWithItsViewAndStatus() {
        final var resolver = PagesExceptionResolver.builder().with(NotFound.class, "errors/not-found", 404).build();
        final var res = new MockHttpServletResponse();
        final var got = resolver.resolveException(new MockHttpServletRequest(), res, null, new SpecificNotFound());
        Assertions.assertEquals("errors/not-found", got.getViewName(), "a subclass of a mapped exception must render the mapped view");
        Assertions.assertEquals(404, res.getStatus(), "a subclass of a mapped exception must be answered with the mapped status");
    }

    @Test
    public void theFirstRegisteredMatchingMappingWins() {
        final var resolver = PagesExceptionResolver.builder()
                .with(NotFound.class, "errors/general")
                .with(SpecificNotFound.class, "errors/specific")
                .build();
        final var got = resolver.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), null, new SpecificNotFound());
        Assertions.assertEquals("errors/general", got.getViewName(), "mappings must be consulted in registration order, not by specificity");
    }

    @Test
    public void aMappingWithoutStatusLeavesTheResponseStatusAlone() {
        final var resolver = PagesExceptionResolver.builder().with(NotFound.class, "errors/not-found").build();
        final var res = new MockHttpServletResponse();
        res.setStatus(409);
        resolver.resolveException(new MockHttpServletRequest(), res, null, new NotFound());
        Assertions.assertEquals(409, res.getStatus(), "a mapping without status must not change the response status");
    }
}
