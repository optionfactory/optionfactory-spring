package net.optionfactory.spring.problems.web;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.optionfactory.spring.problems.Failure;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.json.JsonMapper;

public class ResolverLoggingTest {

    @ResponseBody
    public void fakeControllerMethod() {

    }

    private static final class Capture extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        private Capture() {
            super("capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    private LoggerContext logging;
    private Capture capture;

    @BeforeEach
    public void setup() {
        logging = (LoggerContext) LogManager.getContext(false);
        capture = new Capture();
        capture.start();
        logging.getConfiguration().getRootLogger().addAppender(capture, Level.ALL, null);
        logging.updateLoggers();
    }

    @AfterEach
    public void teardown() {
        logging.getConfiguration().getRootLogger().removeAppender(capture.getName());
        logging.updateLoggers();
        capture.stop();
    }

    private List<Level> resolverLevelsWhenResolving(Exception ex) throws NoSuchMethodException {
        final var er = RestExceptionResolver.builder().build(new JsonMapper());
        final var handler = new HandlerMethod(new ResolverLoggingTest(), ResolverLoggingTest.class.getMethod("fakeControllerMethod"));
        er.resolveException(new MockHttpServletRequest(), new MockHttpServletResponse(), handler, ex);
        return capture.events.stream()
                .filter(e -> RestExceptionResolver.class.getName().equals(e.getLoggerName()))
                .map(LogEvent::getLevel)
                .toList();
    }

    @Test
    public void aClientErrorIsNotLoggedAsAWarning() throws NoSuchMethodException {
        final var levels = resolverLevelsWhenResolving(Failure.field("name", "required"));
        Assertions.assertTrue(levels.stream().noneMatch(level -> level.isMoreSpecificThan(Level.WARN)), levels.toString());
    }

    @Test
    public void anUnexpectedErrorIsStillLoggedAsAnError() throws NoSuchMethodException {
        final var levels = resolverLevelsWhenResolving(new IllegalStateException("a bug"));
        Assertions.assertTrue(levels.contains(Level.ERROR), levels.toString());
    }
}
