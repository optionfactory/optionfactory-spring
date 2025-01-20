package net.optionfactory.spring.csp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;
import net.optionfactory.spring.client.reports.ClientReportFilter;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyReportFilter.CspViolation;
import org.springframework.context.ApplicationEventPublisher;

public class StrictContentSecurityPolicyReportFilter extends ClientReportFilter<CspViolation> {

    public StrictContentSecurityPolicyReportFilter(String reportUri, ApplicationEventPublisher publisher, int maxBodySize, boolean log, Function<Object, String> principalRenderer) {
        super("csp-violation", reportUri, publisher, maxBodySize, log, CspViolation::new, principalRenderer);
    }

    public record CspViolation(Object principal, JsonNode content) {

    }

}
