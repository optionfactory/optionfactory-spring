package net.optionfactory.spring.csp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyNonceFilter.Csp;
import org.springframework.security.web.header.HeaderWriter;

public class StrictContentSecurityPolicyHeaderWriter implements HeaderWriter {

    public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    public static final String CONTENT_SECURITY_POLICY_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    public enum ContentSecurityPolicyMode {
        DISABLE, ENFORCE, REPORT
    }

    private final ContentSecurityPolicyMode mode;
    private final String directives;

    public StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode mode) {
        this.mode = mode;
        this.directives = Stream.of(
                "object-src 'none'",
                "script-src 'nonce-{cspnonce}' 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:",
                "base-uri 'self'",
                "report-uri /csp-violations/"
        ).collect(Collectors.joining(";"));
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (mode == ContentSecurityPolicyMode.DISABLE) {
            return;
        }
        final String header = mode == ContentSecurityPolicyMode.ENFORCE ? CONTENT_SECURITY_POLICY_HEADER : CONTENT_SECURITY_POLICY_REPORT_ONLY_HEADER;
        if (request.getAttribute("csp") instanceof Csp csp) {
            response.setHeader(header, directives.replace("{cspnonce}", csp.nonce()));
            return;
        }
        throw new IllegalStateException("cspnonce filter is not configured");
    }

}
