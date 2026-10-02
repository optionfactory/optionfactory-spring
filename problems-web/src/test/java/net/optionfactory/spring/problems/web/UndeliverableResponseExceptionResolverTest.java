package net.optionfactory.spring.problems.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

public class UndeliverableResponseExceptionResolverTest {

    private final UndeliverableResponseExceptionResolver resolver = new UndeliverableResponseExceptionResolver();

    @Test
    public void anOrdinaryExceptionIsLeftToTheResolversBehind() {
        final var got = resolver.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), null, new IllegalStateException("boom"));
        Assertions.assertNull(got, "an ordinary exception on a live response must be declined");
    }

    @Test
    public void aDisconnectedClientIsAnsweredWithNothing() {
        final var got = resolver.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), null,
                new AsyncRequestNotUsableException("Servlet container error notification for disconnected client"));
        Assertions.assertNotNull(got, "a disconnected client must be answered, so no other resolver tries");
        Assertions.assertTrue(got.isEmpty(), "an empty ModelAndView means handled, render nothing");
    }

    @Test
    public void aDisconnectIsFoundThroughTheCauseChain() {
        final var wrapped = new IllegalStateException("while flushing", new IOException("Broken pipe"));
        final var got = resolver.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), null, wrapped);
        Assertions.assertNotNull(got, "a disconnect wrapped in another exception must be recognized");
        Assertions.assertTrue(got.isEmpty(), "an empty ModelAndView means handled, render nothing");
    }

    @Test
    public void aFaultOnACommittedResponseIsAnsweredWithNothing() {
        final var response = new MockHttpServletResponse();
        response.setCommitted(true);
        final var got = resolver.resolveException(new MockHttpServletRequest(), response, null, new IllegalStateException("boom"));
        Assertions.assertNotNull(got, "a fault on a committed response must be answered, so no other resolver tries");
        Assertions.assertTrue(got.isEmpty(), "an empty ModelAndView means handled, render nothing");
    }

    @Test
    public void itIsPlacedAheadOfEveryOtherResolver() {
        final List<HandlerExceptionResolver> chain = new ArrayList<>();
        ExceptionResolvers.configurer(chain)
                .rest(new JsonMapper())
                .binaries()
                .pages()
                .undeliverables()
                .configure();
        Assertions.assertInstanceOf(UndeliverableResponseExceptionResolver.class, chain.getFirst(), "the resolver must come first, whatever the order it was configured in");
    }

    @Test
    public void itIsAbsentUnlessConfigured() {
        final List<HandlerExceptionResolver> chain = new ArrayList<>();
        ExceptionResolvers.configurer(chain).rest(new JsonMapper()).pages().configure();
        Assertions.assertTrue(chain.stream().noneMatch(r -> r instanceof UndeliverableResponseExceptionResolver), "the resolver must only be installed when asked for");
    }
}
