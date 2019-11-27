package net.optionfactory.security.web.csp;

import java.security.SecureRandom;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

public class ContentSecurityPolicyHeaderWriter implements HeaderWriter {

    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    private static final String CONTENT_SECURITY_POLICY_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    public enum ContentSecurityPolicyMode {
        DISABLE, ENFORCE, REPORT
    }
    public enum Nonce {
        GENERATE, DISABLE;
    }
    
    private final SecureRandom sr = new SecureRandom();
    private final ContentSecurityPolicyMode mode;
    private final Nonce nonce;
    private final Map<String, BiFunction<String, String, String>> allowedSources;


    public ContentSecurityPolicyHeaderWriter(ContentSecurityPolicyMode mode, Nonce nonce, Map<String, BiFunction<String, String, String>> allowedSources) {
        this.mode = mode;
        this.nonce = nonce;
        this.allowedSources = allowedSources;
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (mode == ContentSecurityPolicyMode.DISABLE) {
            return;
        }
        final String header = mode == ContentSecurityPolicyMode.ENFORCE ? CONTENT_SECURITY_POLICY_HEADER : CONTENT_SECURITY_POLICY_REPORT_ONLY_HEADER;
        final String hexNonce = nonce == Nonce.GENERATE ? makeNonce() : "";
        
        final String directives = allowedSources.entrySet().stream().map(kv -> 
                String.format("%s %s", kv.getKey(), kv.getValue().apply(request.getRequestURI(), hexNonce))
        ).collect(Collectors.joining("; "));
        
        request.setAttribute("cspnonce", hexNonce);
        response.setHeader(header, directives);
    }

    public String makeNonce() {
        final byte[] bytes = new byte[16];
        sr.nextBytes(bytes);
        final char[] chars = new char[32];
        for (int i = 0; i != bytes.length; i++) {
            final byte b = bytes[i];
            chars[i*2] = Character.forDigit((b >> 4) & 0xF, 16);
            chars[i*2+1] = Character.forDigit((b & 0xF), 16);
        }
        return new String(chars);
    }
}
