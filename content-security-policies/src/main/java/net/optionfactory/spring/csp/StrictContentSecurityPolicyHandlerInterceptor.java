package net.optionfactory.spring.csp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

public class StrictContentSecurityPolicyHandlerInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mav) throws Exception {
        if (mav == null) {
            return;
        }
        if (isRedirectView(mav)) {
            return;
        }
        mav.addObject("cspnonce", request.getAttribute("cspnonce"));
    }

    private boolean isRedirectView(ModelAndView mav) {
        if (mav.getView() instanceof RedirectView) {
            return true;
        }
        final var vn = mav.getViewName();
        return vn != null && vn.startsWith("redirect:");
    }
}
