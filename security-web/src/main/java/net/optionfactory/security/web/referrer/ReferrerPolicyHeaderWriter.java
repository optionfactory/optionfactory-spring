package net.optionfactory.security.web.referrer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

public class ReferrerPolicyHeaderWriter implements HeaderWriter {

    public enum ReferrerPolicy {
        NO_REFERRER("no-referrer"),
        NO_REFERRER_WHEN_DOWNGRADE("no-referrer-when-downgrade"),
        ORIGIN("origin"),
        ORIGIN_WHEN_CROSS_ORIGIN("origin-when-cross-origin"),
        SAME_ORIGIN("same-origin"),
        STRICT_ORIGIN("strict-origin"),
        STRICT_ORIGIN_WHEN_CROSS_ORIGIN("strict-origin-when-cross-origin"),
        UNSAFE_URL("unsafe-url");
        public final String value;

        ReferrerPolicy(String value) {
            this.value = value;
        }
    }

    private final ReferrerPolicy policy;

    public ReferrerPolicyHeaderWriter(ReferrerPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Referrer-Policy", this.policy.value);
    }
}
