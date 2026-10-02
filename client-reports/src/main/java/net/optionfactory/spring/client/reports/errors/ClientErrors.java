package net.optionfactory.spring.client.reports.errors;

import java.util.function.Function;
import net.optionfactory.spring.client.reports.ClientReportFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import tools.jackson.databind.JsonNode;

/// Collects the errors browsers report, as [ClientError] events published to the application
/// context.
///
/// ```java
/// http.with(ClientErrors.configurer(), c -> c
///         .reportUri("/api/client-errors")
///         .log(true, principal -> String.format("[user:%s]", principal)));
/// ```
///
/// Clients then POST a json description of their errors to the report uri, and the application
/// reacts with an `@EventListener` on [ClientError].
public class ClientErrors {

    /// @return a configurer with the default settings
    public static Configurer configurer() {
        return new Configurer();
    }

    /// Adds a [ClientErrorReportFilter] to the security filter chain, right before the
    /// `HeaderWriterFilter`.
    ///
    /// That position is deliberate: the security context has already been loaded, so a report
    /// carries the principal of a session, while csrf protection, authentication filters and
    /// authorization rules have not run yet, so reports are accepted from anyone, including clients
    /// whose session expired. Two consequences follow: a principal established per request (e.g.
    /// from a bearer token) is not known yet, so those reports carry a `null` principal; and,
    /// without csrf protection, a report is attributed to whoever's session cookie comes with it,
    /// so a report must never be trusted as an action of its principal. Session cookies marked
    /// `SameSite=Lax` or `Strict` (the `@EmbeddedTomcatWebMvcApplication` default is `Lax`) are not
    /// sent with a cross-site POST, which keeps another site from making a visitor's browser post
    /// a report attributed to the visitor; with `SameSite=None` it can.
    ///
    /// Events are published to the `ApplicationContext` shared by `HttpSecurity`.
    public static class Configurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private String reportUri = "/client-errors/";
        private int maxBodySize = 65_536;
        private boolean log = true;
        private Function<Object, String> principalRenderer = (p) -> String.format("[user:%s]", p);

        /// @param uri the path reports are posted to, relative to the context path, matched
        /// exactly; default `/client-errors/`
        /// @return this configurer
        public Configurer reportUri(String uri) {
            this.reportUri = uri;
            return this;
        }

        /// @param maxBodySize the number of body bytes read, beyond which a report is published as
        /// unparseable; default 64KiB
        /// @return this configurer
        public Configurer maxBodySize(int maxBodySize) {
            this.maxBodySize = maxBodySize;
            return this;
        }

        /// @param enabled whether each report is also logged at WARN by the `client-error`
        /// logger; default true
        /// @return this configurer
        public Configurer log(boolean enabled) {
            this.log = enabled;
            return this;
        }

        /// @param enabled whether each report is also logged at WARN by the `client-error` logger
        /// @param principalRenderer renders the principal, possibly `null`, in the log line;
        /// default `[user:<principal>]`
        /// @return this configurer
        public Configurer log(boolean enabled, Function<Object, String> principalRenderer) {
            this.log = enabled;
            this.principalRenderer = principalRenderer;
            return this;
        }

        /// @param http the security builder the filter is added to
        @Override
        public void configure(HttpSecurity http) {
            final var publisher = http.getSharedObject(ApplicationContext.class);
            http.addFilterBefore(new ClientErrorReportFilter(reportUri, publisher, maxBodySize, log, principalRenderer), HeaderWriterFilter.class);
        }

    }

    /// A [ClientReportFilter] named `client-error` that publishes [ClientError] events.
    public static class ClientErrorReportFilter extends ClientReportFilter<ClientError> {

        /// @param reportUri the path reports are posted to, relative to the context path
        /// @param publisher receives the events
        /// @param maxBodySize the number of body bytes read
        /// @param log whether each report is also logged
        /// @param principalRenderer renders the principal in the log line
        public ClientErrorReportFilter(String reportUri, ApplicationEventPublisher publisher, int maxBodySize, boolean log, Function<Object, String> principalRenderer) {
            super("client-error", reportUri, publisher, maxBodySize, log, ClientError::new, principalRenderer);
        }

    }

    /// An error reported by a browser.
    ///
    /// @param principal the principal of the security context when the report was received,
    /// `null` when anonymous
    /// @param content the report as posted, or the text node `"unparseable report"` when it was
    /// empty, too large or not json
    public record ClientError(Object principal, JsonNode content) {

    }

}
