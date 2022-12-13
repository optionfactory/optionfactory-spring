package net.optionfactory.spring.feedback.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

public class FeedbackLogger<P> {

    protected final Logger logger = LoggerFactory.getLogger(FeedbackLogger.class);
    private final int maxBytesToLog;
    private final Function<P, String> principalToPrefix;

    public FeedbackLogger(int maxBytesToLog, Function<P, String> principalToPrefix) {
        this.maxBytesToLog = maxBytesToLog;
        this.principalToPrefix = principalToPrefix;
    }

    public void report(String type, P principal, HttpServletRequest req) {
        logger.warn(String.format("[op:%s]%s %s", type, principalToPrefix.apply(principal), bodyAsLine(req)));
    }

    private String bodyAsLine(HttpServletRequest req) {
        try (final var is = req.getInputStream(); final var baos = new ByteArrayOutputStream()) {
            StreamUtils.copyRange(is, baos, 0, maxBytesToLog);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8).replace("\r", "").replace("\n", " ");
        } catch (IOException ex) {
            return "unparseable binary body";
        }
    }
}
