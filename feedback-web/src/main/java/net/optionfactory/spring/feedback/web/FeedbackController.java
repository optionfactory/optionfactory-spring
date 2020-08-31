package net.optionfactory.spring.feedback.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;

public class FeedbackController<PRINCIPAL> {

    private final Logger logger = LoggerFactory.getLogger(FeedbackController.class);
    private final Function<PRINCIPAL, String> principalToPrefix;

    public FeedbackController(Function<PRINCIPAL, String> principalToPrefix) {
        this.principalToPrefix = principalToPrefix;
    }
    
    @PostMapping("/client-errors/")
    public void clientErrors(@AuthenticationPrincipal PRINCIPAL auth, HttpServletRequest req) {
        logger.warn("[op:client-error]{} client-error: {}", principalToPrefix.apply(auth), bodyAsLine(req));
    }

    @PostMapping("/csp-violations/")
    public void cspViolation(@AuthenticationPrincipal PRINCIPAL auth, HttpServletRequest req) {
        logger.warn("[op:csp-violation]{} csp-violation: {}", principalToPrefix.apply(auth), bodyAsLine(req));
    }

    
    private static final int MAX_BYTES_TO_LOG = 65536;

    private static String bodyAsLine(HttpServletRequest req) {
        try (final var is = req.getInputStream(); final var baos = new ByteArrayOutputStream()) {
            StreamUtils.copyRange(is, baos, 0, MAX_BYTES_TO_LOG);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8).replace("\r", "").replace("\n", " ");
        } catch (IOException ex) {
            return "unparseable binary body";
        }
    }
}
