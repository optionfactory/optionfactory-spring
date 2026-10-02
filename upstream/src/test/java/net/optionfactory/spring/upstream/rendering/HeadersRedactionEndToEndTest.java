package net.optionfactory.spring.upstream.rendering;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamBuilder;
import net.optionfactory.spring.upstream.log.UpstreamLoggingInterceptor;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering.HeadersStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

public class HeadersRedactionEndToEndTest {

    @Upstream("redacting")
    public interface RedactingClient {

        @GetExchange("/a")
        String get(@RequestHeader("Authorization") String authorization);
    }

    private static class CapturingAppender extends AbstractAppender {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        public CapturingAppender() {
            super("capturing", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }

    @Test
    public void redactingALoggedHeaderDoesNotChangeTheRequestSentUpstream() {
        final var sent = new AtomicReference<HttpHeaders>();
        final var d = Upstream.Logging.Conf.defaults();
        final var client = UpstreamBuilder.create(RedactingClient.class)
                .requestFactory((uri, method) -> new MockClientHttpRequest(method, uri) {
                    @Override
                    protected ClientHttpResponse executeInternal() {
                        sent.set(HttpHeaders.copyOf(getHeaders()));
                        final var response = new MockClientHttpResponse("ok".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
                        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                        return response;
                    }
                })
                .redact(r -> r.header("Authorization"))
                .logging(new Upstream.Logging.Conf(d.requestMultipart(), HeadersStrategy.CONTENT, d.requestBody(), d.requestMaxSize(), d.responseMultipart(), d.responseHeaders(), d.responseBody(), d.responseMaxSize(), d.infix()))
                .baseUri("http://example.com")
                .build();

        final var appender = new CapturingAppender();
        appender.start();
        final var logger = (Logger) LogManager.getLogger(UpstreamLoggingInterceptor.class);
        logger.addAppender(appender);
        try {
            client.get("Bearer real-token");
        } finally {
            logger.removeAppender(appender);
        }
        Assertions.assertEquals("Bearer real-token", sent.get().getFirst("Authorization"), "the request sent upstream must keep the real Authorization header");
        Assertions.assertTrue(appender.messages.stream().anyMatch(m -> m.contains("Authorization") && m.contains("@redacted@")), "the logged headers must be redacted: " + appender.messages);
        Assertions.assertTrue(appender.messages.stream().noneMatch(m -> m.contains("real-token")), "the real token must never be logged: " + appender.messages);
    }
}
