package net.optionfactory.spring.csp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyNonceFilter.Csp;
import org.springframework.security.web.header.HeaderWriter;

/// Writes the strict content security policy, rendered with the request's nonce:
///
/// ```text
/// object-src 'none';script-src 'nonce-<nonce>' 'strict-dynamic' 'unsafe-eval' 'unsafe-inline' https:;base-uri 'self';report-uri <reportUri>
/// ```
///
/// where `'unsafe-eval'` and `'unsafe-inline' https:` are only present when enabled. The nonce is
/// read from the `csp` request attribute set by [StrictContentSecurityPolicyNonceFilter].
public class StrictContentSecurityPolicyHeaderWriter implements HeaderWriter {

    /// The header of an enforced policy.
    public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    /// The header of a policy whose violations are reported, not blocked.
    public static final String CONTENT_SECURITY_POLICY_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    /// Whether, and how, the policy is sent.
    public enum ContentSecurityPolicyMode {
        /// No header is written.
        DISABLE,
        /// The policy is sent as `Content-Security-Policy`: browsers block what it forbids.
        ENFORCE,
        /// The policy is sent as `Content-Security-Policy-Report-Only`: browsers only report what it
        /// forbids, useful to try a policy out on a live application.
        REPORT
    }

    private final ContentSecurityPolicyMode mode;
    private final String directives;

    /// @param mode whether, and how, the policy is sent
    /// @param reportUri where browsers post violation reports
    /// @param eval whether `'unsafe-eval'` is allowed
    /// @param fallbacks whether `'unsafe-inline' https:` is added for browsers without nonce support
    public StrictContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode mode, String reportUri, boolean eval, boolean fallbacks) {
        this.mode = mode;
        final var evalStmt = eval ? " 'unsafe-eval'" : "";
        final var fallbackStmts = fallbacks ? " 'unsafe-inline' https:" : "";
        
        this.directives = Stream.of(
                "object-src 'none'",
                "script-src 'nonce-{cspnonce}' 'strict-dynamic'%s%s".formatted(evalStmt, fallbackStmts),
                "base-uri 'self'",
                "report-uri %s".formatted(reportUri)
        ).collect(Collectors.joining(";"));
    }

    /// Sets the policy header for the mode, nothing when disabled.
    ///
    /// @throws IllegalStateException when the mode is not `DISABLE` and the request carries no
    /// nonce: [StrictContentSecurityPolicyNonceFilter] is not in front of the header writer
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
