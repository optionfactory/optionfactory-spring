package net.optionfactory.spring.data.jpa.test;

import java.util.Arrays;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/// Runs fixture test classes in-process through the JUnit Platform launcher, so that extension and listener
/// behaviour can be asserted from the outside, including what happens after the last test of a run.
public class Jupiter {

    public static TestExecutionSummary run(Class<?>... testClasses) {
        final var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(Arrays.stream(testClasses).map(DiscoverySelectors::selectClass).toList())
                .build();
        final var listener = new SummaryGeneratingListener();
        try (var session = LauncherFactory.openSession()) {
            session.getLauncher().execute(request, listener);
        }
        return listener.getSummary();
    }
}
