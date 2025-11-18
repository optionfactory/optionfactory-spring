package net.optionfactory.spring.csp;

import java.util.function.Function;
import net.optionfactory.spring.client.reports.ClientReportFilter;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyReportFilter.CspViolation;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;

public class StrictContentSecurityPolicyReportFilter extends ClientReportFilter<CspViolation> {

    public StrictContentSecurityPolicyReportFilter(String reportUri, ApplicationEventPublisher publisher, int maxBodySize, boolean log, Function<Object, String> principalRenderer) {
        super("csp-violation", reportUri, publisher, maxBodySize, log, CspViolation::new, principalRenderer);
    }

    public record CspViolation(Object principal, JsonNode content) {

    }

}
