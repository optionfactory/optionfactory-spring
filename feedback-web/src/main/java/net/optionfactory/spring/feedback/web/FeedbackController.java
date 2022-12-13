package net.optionfactory.spring.feedback.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;

public class FeedbackController<PRINCIPAL> {

    private final FeedbackLogger<PRINCIPAL> logger;

    public FeedbackController(FeedbackLogger<PRINCIPAL> logger) {
        this.logger = logger;
    }
    
    @PostMapping("/client-errors/")
    public void clientErrors(@AuthenticationPrincipal PRINCIPAL auth, HttpServletRequest req) {
        logger.report("client-error", auth, req);
    }

    @PostMapping("/csp-violations/")
    public void cspViolation(@AuthenticationPrincipal PRINCIPAL auth, HttpServletRequest req) {
        logger.report("csp-violation", auth, req);
    }

    
}
