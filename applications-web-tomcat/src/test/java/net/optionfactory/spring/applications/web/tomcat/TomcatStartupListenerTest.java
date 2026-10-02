package net.optionfactory.spring.applications.web.tomcat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

public class TomcatStartupListenerTest {

    @Test
    public void logsThroughItsOwnLogger() throws Exception {
        final var field = TomcatStartupListener.class.getDeclaredField("logger");
        field.setAccessible(true);
        final var logger = (Logger) field.get(null);
        Assertions.assertEquals(TomcatStartupListener.class.getName(), logger.getName(), "the startup listener logs under its own class name");
    }
}
