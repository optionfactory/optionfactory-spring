package net.optionfactory.spring.upstream.alerts.spooler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.email.EmailSpooled;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageConverters;

public class AlertsEmailsSpoolerBuilderTest {

    @TempDir
    Path tmp;

    private final GenericApplicationContext ac = new GenericApplicationContext();
    private final ManualTaskScheduler ts = new ManualTaskScheduler();
    private final List<Object> spooledEvents = new ArrayList<>();

    @AfterEach
    public void close() {
        ac.close();
    }

    private EmailPaths paths() {
        return EmailPaths.provide(tmp.resolve("spool"), null, null);
    }

    private void refresh() {
        ac.addApplicationListener((ApplicationListener<ApplicationEvent>) e -> {
            if (e instanceof PayloadApplicationEvent<?> pe && pe.getPayload() instanceof EmailSpooled) {
                spooledEvents.add(pe.getPayload());
            }
        });
        ac.refresh();
    }

    private static EmailMessage.Prototype unspooledPrototype() {
        return EmailMessage.builder()
                .sender("test@example.com", null)
                .recipient("recipient@example.com")
                .subject("subject")
                .htmlBodyEngine(AlertsEmailsSpooler.templateEngine("/email/", null))
                .htmlBodyTemplate("example-email.alerts.inlined.html")
                .prototype();
    }

    private static List<Path> spooled(Path spool) {
        try (var files = Files.list(spool)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".eml")).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    public void aSinglePrototypeSpoolsTheWholeBatchAsOneAnnouncedAlertsEmail() throws Exception {
        final var paths = paths();
        AlertsEmailsSpooler.builder(paths, ac, ts).bufferedScheduled(unspooledPrototype());
        refresh();

        ac.publishEvent(alert("upstream-a"));
        ac.publishEvent(alert("upstream-b"));
        ts.runOneShots();

        final var emls = spooled(paths.spool());
        Assertions.assertEquals(1, emls.size(), "all the alerts of a batch go into one email");
        Assertions.assertTrue(emls.get(0).getFileName().toString().startsWith("alerts."), "the spooled file carries the alerts prefix");
        Assertions.assertEquals(1, spooledEvents.size(), "the spooled email is announced to the application context");
    }

    @Test
    public void theSelectorPrototypesAreGivenTheSpoolingConfigurationOncePerInstance() throws Exception {
        final var paths = paths();
        final var shared = unspooledPrototype();
        final var other = unspooledPrototype();
        final var byUpstream = Map.of("upstream-a", shared, "upstream-b", shared, "upstream-c", other);
        AlertsEmailsSpooler.builder(paths, ac, ts).bufferedScheduled(byUpstream::get);
        refresh();

        ac.publishEvent(alert("upstream-a"));
        ac.publishEvent(alert("upstream-b"));
        ac.publishEvent(alert("upstream-c"));
        ts.runOneShots();

        Assertions.assertEquals(2, spooled(paths.spool()).size(), "upstreams sharing a prototype share an email even after the spooling configuration is applied");
    }

    @Test
    public void anUpstreamWithoutPrototypeLosesOnlyItsAlert() throws Exception {
        final var paths = paths();
        final var prototype = unspooledPrototype();
        AlertsEmailsSpooler.builder(paths, ac, ts).bufferedScheduled(upstream -> "upstream-known".equals(upstream) ? prototype : null);
        refresh();

        ac.publishEvent(alert("upstream-unknown"));
        ac.publishEvent(alert("upstream-known"));
        ts.runOneShots();

        Assertions.assertEquals(1, spooled(paths.spool()).size(), "a null prototype drops its alert, not the batch");
    }

    @Test
    public void theDurationsHaveDefaultsAndCanBeConfigured() {
        final var defaults = new ManualTaskScheduler();
        AlertsEmailsSpooler.builder(paths(), ac, defaults).bufferedScheduled(unspooledPrototype());
        AlertsEmailsSpooler.builder(paths(), ac, ts).rate(Duration.ofMinutes(1)).bufferedScheduled(unspooledPrototype());

        Assertions.assertEquals(Duration.ofMinutes(5), defaults.periodic.get(0).period(), "the buffer is drained every 5 minutes by default");
        Assertions.assertEquals(Duration.ofMinutes(1), ts.periodic.get(0).period(), "the configured rate is used");
    }

    @Test
    public void mandatoryArgumentsAreChecked() {
        final var paths = paths();
        Assertions.assertThrows(IllegalArgumentException.class, () -> AlertsEmailsSpooler.builder(null, ac, ts), "the paths are mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> AlertsEmailsSpooler.builder(paths, null, ts), "the application context is mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> AlertsEmailsSpooler.builder(paths, ac, null), "the scheduler is mandatory");
        Assertions.assertThrows(IllegalArgumentException.class, () -> AlertsEmailsSpooler.builder(paths, ac, ts).bufferedScheduled((EmailMessage.Prototype) null), "the prototype is mandatory");
    }

    private static UpstreamAlertEvent alert(String upstream) throws NoSuchMethodException {
        final var invocation = new InvocationContext(
                new Expressions(null, null),
                PayloadsRendering.builder().build(),
                new InvocationContext.MessageConverters(HttpMessageConverters.forClient().build()),
                new EndpointDescriptor(upstream, "an-endpoint", Object.class.getMethod("toString"), null),
                new Object[0],
                "boot",
                0,
                null,
                Buffering.BUFFERED);
        final var request = new RequestContext(
                Instant.now(),
                HttpMethod.GET,
                URI.create("https://www.example.com"),
                HttpHeaders.EMPTY,
                new HashMap<>(),
                "test request".getBytes(StandardCharsets.UTF_8));
        return new UpstreamAlertEvent(invocation, request, null, new ExceptionContext(Instant.now(), "exception message"));
    }
}
