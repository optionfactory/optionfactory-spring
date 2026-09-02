package net.optionfactory.spring.upstream.alerts.spooler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.EmailPaths;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.ExceptionContext;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageConverters;

public class AlertsEmailsSpoolerRoutingTest {

    @TempDir
    Path tmp;

    @Test
    public void singlePrototypeMarshalsTheWholeBatchIntoOneEmail() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var prototype = prototype(paths, "example-email.alerts.inlined.html");

        final var spooled = new AlertsEmailsSpooler(upstream -> prototype).spool(List.of(alert("upstream-a"), alert("upstream-b")));

        Assertions.assertEquals(1, spooled.size(), "one email for the whole batch, regardless of upstream");
    }

    @Test
    public void distinctPrototypesMarshalOneEmailEach() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var a = prototype(paths, "example-email.alerts.inlined.html");
        final var b = prototype(paths, "example-email.alerts.inlined.html");

        final var spooled = new AlertsEmailsSpooler(upstream -> "upstream-a".equals(upstream) ? a : b)
                .spool(List.of(alert("upstream-a"), alert("upstream-b"), alert("upstream-a")));

        Assertions.assertEquals(2, spooled.size(), "one email per distinct prototype");
    }

    @Test
    public void upstreamsSharingAPrototypeAreBatchedIntoOneEmail() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var shared = prototype(paths, "example-email.alerts.inlined.html");
        final var other = prototype(paths, "example-email.alerts.inlined.html");

        // two upstreams owned by the same people must not produce two near-duplicate emails
        final var spooled = new AlertsEmailsSpooler(upstream -> upstream.startsWith("salesforce") ? shared : other)
                .spool(List.of(alert("salesforce"), alert("salesforce-log")));

        Assertions.assertEquals(1, spooled.size(), "grouping is by prototype, not by upstream");
    }

    @Test
    public void aFailingGroupDoesNotSuppressTheOthers() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var good = prototype(paths, "example-email.alerts.inlined.html");
        final var broken = prototype(paths, "no-such-template.html");

        final var spooled = new AlertsEmailsSpooler(upstream -> "upstream-broken".equals(upstream) ? broken : good)
                .spool(List.of(alert("upstream-broken"), alert("upstream-ok")));

        // the returned list is built with List.copyOf, which rejects nulls, so a
        // dropped group can only ever be absent rather than a null entry
        Assertions.assertEquals(1, spooled.size(), "the healthy upstream is still spooled");
    }

    @Test
    public void aThrowingSelectorLosesOnlyItsOwnGroup() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var good = prototype(paths, "example-email.alerts.inlined.html");

        final var spooled = new AlertsEmailsSpooler(upstream -> {
            if ("upstream-unknown".equals(upstream)) {
                throw new IllegalStateException("no prototype registered");
            }
            return good;
        }).spool(List.of(alert("upstream-unknown"), alert("upstream-ok")));

        Assertions.assertEquals(1, spooled.size(), "a selector failure must not discard the whole drained batch");
    }

    @Test
    public void theLayoutResolvesForATemplateKeptUnderAnyPrefix() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        // the including template lives nowhere near the layout's own /email/ prefix
        final var engine = AlertsEmailsSpooler.templateEngine("/elsewhere/deeply/nested/", null);
        final var prototype = EmailMessage.builder()
                .sender("test@example.com", null)
                .recipient("recipient@example.com")
                .subject("subject")
                .htmlBodyEngine(engine)
                .htmlBodyTemplate("my-alerts.html")
                .spooling(paths, "alerts.", null)
                .prototype();

        final var eml = new String(prototype.builder().variable("alerts", List.of(alert("upstream-a"))).marshal(), StandardCharsets.UTF_8);

        Assertions.assertTrue(eml.contains("Elsewhere"), "the layout rendered the title it was given");
        Assertions.assertTrue(eml.contains("upstream-a"), "the layout iterated the alerts");
        Assertions.assertTrue(eml.contains("an-endpoint"), "the layout rendered a field only it emits");
    }

    private static EmailMessage.Prototype prototype(EmailPaths paths, String template) {
        return EmailMessage.builder()
                .sender("test@example.com", null)
                .recipient("recipient@example.com")
                .subject("subject")
                .htmlBodyEngine(f -> AlertsEmailsSpooler.templateEngine("/email/", null))
                .htmlBodyTemplate(template)
                .spooling(paths, "alerts.", null)
                .prototype();
    }

    private static UpstreamAlertEvent alert(String upstream) throws NoSuchMethodException, IOException {
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
