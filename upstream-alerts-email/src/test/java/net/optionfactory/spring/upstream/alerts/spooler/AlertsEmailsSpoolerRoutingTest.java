package net.optionfactory.spring.upstream.alerts.spooler;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
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

        final var spooled = new AlertsEmailsSpooler(upstream -> upstream.startsWith("salesforce") ? shared : other)
                .spool(List.of(alert("salesforce"), alert("salesforce-log")));

        Assertions.assertEquals(1, spooled.size(), "upstreams owned by the same people get one email, not near-duplicates: grouping is by prototype, not by upstream");
    }

    @Test
    public void aFailingGroupDoesNotSuppressTheOthers() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var good = prototype(paths, "example-email.alerts.inlined.html");
        final var broken = prototype(paths, "no-such-template.html");

        final var spooled = new AlertsEmailsSpooler(upstream -> "upstream-broken".equals(upstream) ? broken : good)
                .spool(List.of(alert("upstream-broken"), alert("upstream-ok")));

        Assertions.assertEquals(1, spooled.size(), "the healthy upstream is still spooled, and the dropped group is absent rather than a null entry");
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

    @Test
    public void aPrototypeWithoutSpoolingLosesOnlyItsGroup() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var good = prototype(paths, "example-email.alerts.inlined.html");
        final var unspooled = EmailMessage.builder()
                .sender("test@example.com", null)
                .recipient("recipient@example.com")
                .subject("subject")
                .htmlBodyEngine(AlertsEmailsSpooler.templateEngine("/email/", null))
                .htmlBodyTemplate("example-email.alerts.inlined.html")
                .prototype();

        final var spooled = new AlertsEmailsSpooler(upstream -> "upstream-unspooled".equals(upstream) ? unspooled : good)
                .spool(List.of(alert("upstream-unspooled"), alert("upstream-ok")));

        Assertions.assertEquals(1, spooled.size(), "a prototype the constructor is given without spooling cannot be marshalled to the spool, and only its group is dropped");
    }

    @Test
    public void aNullPrototypeLosesOnlyItsAlert() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var good = prototype(paths, "example-email.alerts.inlined.html");

        final var spooled = new AlertsEmailsSpooler(upstream -> "upstream-ok".equals(upstream) ? good : null)
                .spool(List.of(alert("upstream-unknown"), alert("upstream-ok")));

        Assertions.assertEquals(1, spooled.size(), "an upstream without prototype drops its own alert only");
    }

    @Test
    public void anEmptyBatchSpoolsNothing() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var prototype = prototype(paths, "example-email.alerts.inlined.html");

        Assertions.assertEquals(List.of(), new AlertsEmailsSpooler(upstream -> prototype).spool(List.of()), "no alerts, no email");
    }

    @Test
    public void alertsKeepTheirEncounterOrderWithinAnEmail() throws Exception {
        final var paths = EmailPaths.provide(Files.createDirectories(tmp.resolve("spool")), null, null);
        final var prototype = prototype(paths, "example-email.alerts.inlined.html");

        final var spooled = new AlertsEmailsSpooler(upstream -> prototype).spool(List.of(alert("upstream-zeta"), alert("upstream-alpha")));

        final var html = htmlBodyOf(spooled.get(0));
        Assertions.assertTrue(html.contains("upstream-zeta") && html.contains("upstream-alpha"), "both alerts are rendered in the same email");
        Assertions.assertTrue(html.indexOf("upstream-zeta") < html.indexOf("upstream-alpha"), "alerts are rendered in the order they were encountered");
    }

    @Test
    public void nullSelectorIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new AlertsEmailsSpooler(null), "a spooler needs a selector");
    }

    private static String htmlBodyOf(Path eml) throws Exception {
        try (var is = Files.newInputStream(eml)) {
            final var message = new MimeMessage(Session.getInstance(new Properties()), is);
            final var alternatives = (Multipart) ((Multipart) message.getContent()).getBodyPart(0).getContent();
            final var related = (Multipart) alternatives.getBodyPart(0).getContent();
            return (String) related.getBodyPart(0).getContent();
        }
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
