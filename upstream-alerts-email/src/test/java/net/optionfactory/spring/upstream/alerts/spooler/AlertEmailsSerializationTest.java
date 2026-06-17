package net.optionfactory.spring.upstream.alerts.spooler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import net.optionfactory.spring.email.EmailMessage;
import net.optionfactory.spring.email.inliner.CssInliner;
import net.optionfactory.spring.thymeleaf.SingletonDialect;
import net.optionfactory.spring.upstream.alerts.UpstreamAlertEvent;
import net.optionfactory.spring.upstream.buffering.Buffering;
import net.optionfactory.spring.upstream.contexts.EndpointDescriptor;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext.BodySource;
import net.optionfactory.spring.upstream.expressions.Expressions;
import net.optionfactory.spring.upstream.rendering.PayloadsRendering;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;

public class AlertEmailsSerializationTest {

    @Test
    public void handlesMultipartFormData() throws Exception {

        final var requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=boundary-123"));
        final var requestBody = String.join("\r\n", List.of(
                "--boundary-123",
                "Content-Disposition: form-data; name=\"username\"",
                "",
                "jdoe",
                "--boundary-123",
                "Content-Disposition: form-data; name=\"role\"",
                "",
                "admin",
                "--boundary-123",
                "Content-Disposition: form-data; name=\"active\"",
                "",
                "true",
                "--boundary-123--"
        )).getBytes(StandardCharsets.UTF_8);

        final var alerts = makeAlert(requestHeaders, requestBody);

        final var emailBytes = EmailMessage.builder()
                .sender("test@example.com", null)
                .recipient("recipient@example.com")
                .subject("Subject")
                .htmlBodyEngine(f -> f.html("/email/", null, new SingletonDialect("bodies", new AlertBodiesFunctions())))
                .htmlBodyTemplate("example-email.alerts.inlined.html")
                .htmlBodyPostprocessor(new CssInliner())
                .variable("alerts", alerts)
                .marshal(Path.of("target/multipart-form-data-test.eml"));

    }
    @Test
    public void handlesMultipartUpload() throws Exception {

        final var requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=boundary-123"));
        final var requestBody = String.join("\r\n", List.of(
                "--boundary-123",
                "Content-Disposition: form-data; name=\"metadata\"",
                "Content-Type: application/json",
                "",
                "{\"id\": 101, \"name\": \"Sensor\"}",
                "--boundary-123",
                "Content-Disposition: form-data; name=\"icon\"",
                "Content-Type: image/svg+xml",
                "",
                "<svg width=\"100\" height=\"100\"><circle cx=\"50\" cy=\"50\" r=\"40\" /></svg>",
                "--boundary-123--"
        )).getBytes(StandardCharsets.UTF_8);

        final var alerts = makeAlert(requestHeaders, requestBody);

        final var emailBytes = EmailMessage.builder()
                .sender("test@example.com", null)
                .recipient("recipient@example.com")
                .subject("Subject")
                .htmlBodyEngine(f -> f.html("/email/", null, new SingletonDialect("bodies", new AlertBodiesFunctions())))
                .htmlBodyTemplate("example-email.alerts.inlined.html")
                .htmlBodyPostprocessor(new CssInliner())
                .variable("alerts", alerts)
                .marshal(Path.of("target/multipart-upload.eml"));

    }

    public List<UpstreamAlertEvent> makeAlert(final HttpHeaders requestHeaders, final byte[] requestBody) throws NoSuchMethodException {
        final var alerts = List.of(
                new UpstreamAlertEvent(
                        new InvocationContext(
                                new Expressions(null, null),
                                PayloadsRendering.builder().build(),
                                new InvocationContext.MessageConverters(HttpMessageConverters.forClient().build()),
                                new EndpointDescriptor("upstream", "endpoint", Object.class.getMethod("toString"), null),
                                new Object[0],
                                "boot-id1",
                                1,
                                null,
                                Buffering.BUFFERED
                        ),
                        new RequestContext(
                                Instant.now(),
                                HttpMethod.PATCH,
                                URI.create("https://example.com"),
                                requestHeaders,
                                new HashMap<>(),
                                requestBody
                        ),
                        new ResponseContext(
                                Instant.now(),
                                HttpStatus.OK,
                                HttpStatus.OK.getReasonPhrase(),
                                new HttpHeaders(),
                                BodySource.of("response_body1".repeat(1000), StandardCharsets.UTF_8),
                                false
                        ),
                        null)
        );
        return alerts;
    }
}
