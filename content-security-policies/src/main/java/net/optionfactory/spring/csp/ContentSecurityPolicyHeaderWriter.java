package net.optionfactory.spring.csp;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.web.header.HeaderWriter;

public class ContentSecurityPolicyHeaderWriter implements HeaderWriter {

    public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    public static final String CONTENT_SECURITY_POLICY_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    public enum ContentSecurityPolicyMode {
        DISABLE, ENFORCE, REPORT
    }

    private final SecureRandom sr = new SecureRandom();
    private final ContentSecurityPolicyMode mode;
    private final String directives;
    private final boolean useNonce;

    public ContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode mode, String directives) {
        this.mode = mode;
        this.directives = directives;
        this.useNonce = directives.contains("{cspnonce}");
    }

    /**
     * Creates a CSP header writer implementing the strict policy as per
     * https://csp.withgoogle.com/docs/strict-csp.html
     *
     * @param mode reporting mode
     * @return the ContentSecurityPolicyHeaderWriter to be configured
     */
    public static ContentSecurityPolicyHeaderWriter strict(ContentSecurityPolicyMode mode) {
        final var directives = Stream.of(
                "object-src 'none'",
                "script-src 'nonce-{cspnonce}' 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:",
                "base-uri 'none'",
                "report-uri /csp-violations/"
        ).collect(Collectors.joining(";"));

        return new ContentSecurityPolicyHeaderWriter(mode, directives);

    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (mode == ContentSecurityPolicyMode.DISABLE) {
            return;
        }
        final String header = mode == ContentSecurityPolicyMode.ENFORCE ? CONTENT_SECURITY_POLICY_HEADER : CONTENT_SECURITY_POLICY_REPORT_ONLY_HEADER;
        if (!useNonce) {
            response.setHeader(header, directives);
            return;
        }
        final byte[] nonce = new byte[16];
        sr.nextBytes(nonce);
        final String hexNonce = new String(Hex.encode(nonce));
        request.setAttribute("cspnonce", hexNonce);
        response.setHeader(header, directives.replace("{cspnonce}", hexNonce));
    }

}
