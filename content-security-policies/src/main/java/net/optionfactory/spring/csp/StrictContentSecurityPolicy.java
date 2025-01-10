package net.optionfactory.spring.csp;

import net.optionfactory.spring.csp.StrictContentSecurityPolicyHeaderWriter.ContentSecurityPolicyMode;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.servlet.HandlerInterceptor;

public class StrictContentSecurityPolicy {

    public static Configurer configurer() {
        return new Configurer();
    }

    public static HandlerInterceptor addNonceToModel() {
        return new StrictContentSecurityPolicyHandlerInterceptor();                
    }
    
    
    public static class Configurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

        private ContentSecurityPolicyMode mode = ContentSecurityPolicyMode.ENFORCE;
        private String reportUri = "/csp-violations/";
        private boolean eval = true;
        private boolean fallbacks = true;

        public Configurer mode(ContentSecurityPolicyMode mode) {
            this.mode = mode;
            return this;
        }

        public Configurer reportUri(String uri) {
            this.reportUri = uri;
            return this;
        }

        public Configurer eval(boolean enabled) {
            this.eval = enabled;
            return this;
        }

        public Configurer fallbacks(boolean enabled) {
            this.fallbacks = enabled;
            return this;
        }

        @Override
        public void init(HttpSecurity http) throws Exception {
            http.headers(c -> {
                c.addHeaderWriter(new StrictContentSecurityPolicyHeaderWriter(mode, reportUri, eval, fallbacks));
            });
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http.addFilterBefore(new StrictContentSecurityPolicyNonceFilter(), HeaderWriterFilter.class);
        }

    }

}
