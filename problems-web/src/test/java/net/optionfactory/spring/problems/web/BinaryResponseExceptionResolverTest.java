package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public class BinaryResponseExceptionResolverTest {

    public static class Handlers {

        public String page() {
            return "page";
        }

        public Resource resource() {
            return null;
        }

        public StreamingResponseBody streaming() {
            return null;
        }

        public ResponseEntity<byte[]> entityOfBytes() {
            return null;
        }

        public ResponseEntity<String> entityOfString() {
            return null;
        }

        public void writing(HttpServletResponse response) {
        }

        public void streamingTo(OutputStream out) {
        }

        @GetMapping(value = "/report", produces = "application/pdf")
        public Object converted() {
            return null;
        }

        @BinaryResponseErrorStatus(HttpStatus.SERVICE_UNAVAILABLE)
        public String annotated() {
            return null;
        }
    }

    private final BinaryResponseExceptionResolver resolver = new BinaryResponseExceptionResolver();

    private static HandlerMethod handler(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return new HandlerMethod(new Handlers(), Handlers.class.getMethod(name, parameterTypes));
    }

    private ModelAndView resolve(HandlerMethod handler, MockHttpServletResponse response, Exception ex) {
        return resolver.resolveException(new MockHttpServletRequest(), response, handler, ex);
    }

    @Test
    public void aHandlerThatIsNotAHandlerMethodIsDeclined() {
        Assertions.assertNull(resolver.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), new IllegalStateException()), "only handler methods can be downloads");
    }

    @Test
    public void aPageHandlerIsDeclined() throws NoSuchMethodException {
        Assertions.assertNull(resolve(handler("page"), new MockHttpServletResponse(), new IllegalStateException()), "a handler returning a view name is no download");
    }

    @Test
    public void downloadsAreDetectedFromTheReturnTypeAndParameters() throws NoSuchMethodException {
        Assertions.assertNotNull(resolve(handler("resource"), new MockHttpServletResponse(), new IllegalStateException()), "returning a Resource makes a download");
        Assertions.assertNotNull(resolve(handler("streaming"), new MockHttpServletResponse(), new IllegalStateException()), "returning a StreamingResponseBody makes a download");
        Assertions.assertNotNull(resolve(handler("entityOfBytes"), new MockHttpServletResponse(), new IllegalStateException()), "returning a ResponseEntity of bytes makes a download");
        Assertions.assertNotNull(resolve(handler("writing", HttpServletResponse.class), new MockHttpServletResponse(), new IllegalStateException()), "a void handler writing to the response makes a download");
        Assertions.assertNotNull(resolve(handler("streamingTo", OutputStream.class), new MockHttpServletResponse(), new IllegalStateException()), "a void handler writing to an OutputStream makes a download");
        Assertions.assertNotNull(resolve(handler("converted"), new MockHttpServletResponse(), new IllegalStateException()), "a handler producing application/pdf makes a download");
    }

    @Test
    public void aResponseEntityOfAnythingElseIsNoDownload() throws NoSuchMethodException {
        Assertions.assertNull(resolve(handler("entityOfString"), new MockHttpServletResponse(), new IllegalStateException()), "a ResponseEntity of a String is no download");
    }

    @Test
    public void aContentDispositionHeaderMakesAnyHandlerADownload() throws NoSuchMethodException {
        final var res = new MockHttpServletResponse();
        res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.csv");
        final var got = resolve(handler("page"), res, new IllegalStateException());
        Assertions.assertNotNull(got, "a response already carrying Content-Disposition must be answered as a download");
        Assertions.assertFalse(res.containsHeader(HttpHeaders.CONTENT_DISPOSITION), "the response must be reset, so the client is not offered a file");
    }

    @Test
    public void aFailedDownloadIsAnsweredWithAStatusAndNoBody() throws Exception {
        final var res = new MockHttpServletResponse();
        res.setHeader("X-Partial", "yes");
        res.getWriter().write("partial");
        final var got = resolve(handler("resource"), res, new IllegalStateException("a bug"));
        Assertions.assertTrue(got.isEmpty(), "an empty ModelAndView means handled, render nothing");
        Assertions.assertEquals(500, res.getStatus(), "an unexpected exception must be answered 500");
        Assertions.assertEquals("", res.getContentAsString(), "what was buffered must be discarded");
        Assertions.assertFalse(res.containsHeader("X-Partial"), "the headers set so far must be discarded");
    }

    @Test
    public void theStatusFollowsTheException() throws NoSuchMethodException {
        final var notFound = new MockHttpServletResponse();
        resolve(handler("resource"), notFound, new ResponseStatusException(HttpStatus.NOT_FOUND));
        Assertions.assertEquals(404, notFound.getStatus(), "a ResponseStatusException must be answered with its own status");
        final var upstream = new MockHttpServletResponse();
        resolve(handler("resource"), upstream, new RestClientException("down"));
        Assertions.assertEquals(502, upstream.getStatus(), "a RestClientException must be answered 502");
    }

    @Test
    public void theAnnotatedStatusWinsOverTheException() throws NoSuchMethodException {
        final var res = new MockHttpServletResponse();
        final var got = resolve(handler("annotated"), res, new ResponseStatusException(HttpStatus.NOT_FOUND));
        Assertions.assertNotNull(got, "an annotated handler must be answered as a download whatever it returns");
        Assertions.assertEquals(503, res.getStatus(), "the status declared with @BinaryResponseErrorStatus must win over the exception's");
    }

    @Test
    public void aCommittedResponseIsLeftAsItIs() throws Exception {
        final var res = new MockHttpServletResponse();
        res.setStatus(200);
        res.getWriter().write("half a file");
        res.flushBuffer();
        final var got = resolve(handler("resource"), res, new IllegalStateException("broken pipe"));
        Assertions.assertTrue(got.isEmpty(), "a failure on a committed download must still be answered, with nothing");
        Assertions.assertEquals(200, res.getStatus(), "the status of a committed response cannot change");
        Assertions.assertEquals("half a file", res.getContentAsString(), "what was sent must stay as it was");
    }
}
