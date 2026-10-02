package net.optionfactory.spring.csp;

import java.util.function.Function;
import net.optionfactory.spring.csp.StrictContentSecurityPolicyHeaderWriter.ContentSecurityPolicyMode;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.servlet.HandlerInterceptor;

/// Entry point for a nonce-based strict content security policy, as recommended by
/// <https://csp.withgoogle.com/docs/strict-csp.html>: scripts run only when they carry the
/// request's nonce, or are loaded by a script that does (`'strict-dynamic'`).
///
/// Three pieces cooperate. A filter generates a fresh nonce for every request and exposes it as the
/// `csp` request attribute; a header writer renders the policy with that nonce; an optional handler
/// interceptor copies the attribute into the model, so that templates can write it, e.g. in
/// thymeleaf `<script th:attr="nonce=${csp.nonce}">`. Violation reports posted by browsers to the
/// report uri are answered with `202 Accepted`, published as [StrictContentSecurityPolicyReportFilter.CspViolation]
/// events and, unless disabled, logged at WARN.
///
/// ```java
/// http.with(StrictContentSecurityPolicy.configurer(), csp -> csp.mode(ContentSecurityPolicyMode.REPORT));
///
/// @Override
/// public void addInterceptors(InterceptorRegistry registry) {
///     registry.addInterceptor(StrictContentSecurityPolicy.addNonceToModel());
/// }
/// ```
public class StrictContentSecurityPolicy {

    /// @return a configurer to register with `HttpSecurity.with(...)`
    public static Configurer configurer() {
        return new Configurer();
    }

    /// @return an interceptor copying the request's nonce into the model of rendered views
    public static HandlerInterceptor addNonceToModel() {
        return new StrictContentSecurityPolicyHandlerInterceptor();
    }

    /// Configures the policy and installs its filters and header writer.
    ///
    /// The defaults enforce the policy, with `'unsafe-eval'` and the fallbacks for browsers without
    /// nonce support allowed, reports posted to `/csp-violations/`, report bodies read up to 64KiB,
    /// and violations logged with the principal rendered as `[user:<principal>]`.
    public static class Configurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private ContentSecurityPolicyMode mode = ContentSecurityPolicyMode.ENFORCE;
        private String reportUri = "/csp-violations/";
        private boolean eval = true;
        private boolean fallbacks = true;
        private int maxBodySize = 65_536;
        private boolean log = true;
        private Function<Object, String> principalRenderer = (p) -> String.format("[user:%s]", p);

        /// @param mode whether the policy is enforced, only reported, or not sent at all
        /// @return this configurer
        public Configurer mode(ContentSecurityPolicyMode mode) {
            this.mode = mode;
            return this;
        }

        /// @param uri where browsers post violation reports, written in the policy's `report-uri`
        /// and matched exactly, without the context path, by the report filter
        /// @return this configurer
        public Configurer reportUri(String uri) {
            this.reportUri = uri;
            return this;
        }

        /// @param enabled whether `'unsafe-eval'` is allowed, for scripts and libraries that need
        /// `eval` or `new Function`
        /// @return this configurer
        public Configurer eval(boolean enabled) {
            this.eval = enabled;
            return this;
        }

        /// @param enabled whether `'unsafe-inline' https:` is added: ignored by browsers supporting
        /// nonces and `'strict-dynamic'`, it keeps older browsers from blocking every script
        /// @return this configurer
        public Configurer fallbacks(boolean enabled) {
            this.fallbacks = enabled;
            return this;
        }

        /// @param maxBodySize the bytes of a report body read at most: a larger report is truncated
        /// and recorded as unparseable
        /// @return this configurer
        public Configurer maxBodySize(int maxBodySize) {
            this.maxBodySize = maxBodySize;
            return this;
        }

        /// @param enabled whether violation reports are logged at WARN, besides being published
        /// @return this configurer
        public Configurer log(boolean enabled) {
            this.log = enabled;
            return this;
        }

        /// @param enabled whether violation reports are logged at WARN, besides being published
        /// @param principalRenderer renders the reporting request's principal, `null` when it is
        /// unauthenticated, in the log line
        /// @return this configurer
        public Configurer log(boolean enabled, Function<Object, String> principalRenderer) {
            this.log = enabled;
            this.principalRenderer = principalRenderer;
            return this;
        }

        /// Adds the [StrictContentSecurityPolicyHeaderWriter] to the chain's headers.
        ///
        /// @param http the security being built
        @Override
        public void init(HttpSecurity http) {
            http.headers(c -> {
                c.addHeaderWriter(new StrictContentSecurityPolicyHeaderWriter(mode, reportUri, eval, fallbacks));
            });
        }

        /// Adds the report filter and the nonce filter before `HeaderWriterFilter`, so that a nonce
        /// exists by the time the headers are written. Events are published through the
        /// application context.
        ///
        /// @param http the security being built
        @Override
        public void configure(HttpSecurity http) {
            final var publisher = http.getSharedObject(ApplicationContext.class);
            http.addFilterBefore(new StrictContentSecurityPolicyReportFilter(reportUri, publisher, maxBodySize, log, principalRenderer), HeaderWriterFilter.class);
            http.addFilterBefore(new StrictContentSecurityPolicyNonceFilter(), HeaderWriterFilter.class);
        }

    }

}
