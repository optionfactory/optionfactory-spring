package net.optionfactory.spring.client.reports.errors;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;
import net.optionfactory.spring.client.reports.ClientReportFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;

public class ClientErrors {

    public static Configurer configurer() {
        return new Configurer();
    }

    public static class Configurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private String reportUri = "/client-errors/";
        private int maxBodySize = 65_536;
        private boolean log = true;
        private Function<Object, String> principalRenderer = (p) -> String.format("[user:%s]", p);

        public Configurer reportUri(String uri) {
            this.reportUri = uri;
            return this;
        }

        public Configurer maxBodySize(int maxBodySize) {
            this.maxBodySize = maxBodySize;
            return this;
        }

        public Configurer log(boolean enabled) {
            this.log = enabled;
            return this;
        }

        public Configurer log(boolean enabled, Function<Object, String> principalRenderer) {
            this.log = enabled;
            this.principalRenderer = principalRenderer;
            return this;
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            final var publisher = http.getSharedObject(ApplicationContext.class);
            http.addFilterBefore(new ClientErrorReportFilter(reportUri, publisher, maxBodySize, log, principalRenderer), HeaderWriterFilter.class);
        }

    }

    public static class ClientErrorReportFilter extends ClientReportFilter<ClientError> {

        public ClientErrorReportFilter(String reportUri, ApplicationEventPublisher publisher, int maxBodySize, boolean log, Function<Object, String> principalRenderer) {
            super("client-error", reportUri, publisher, maxBodySize, log, ClientError::new, principalRenderer);
        }

    }

    public record ClientError(Object principal, JsonNode content) {

    }

}
