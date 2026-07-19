package net.optionfactory.spring.csp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.SmartView;

public class StrictContentSecurityPolicyHandlerInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mav) throws Exception {
        if (mav == null || isRedirect(mav)) {
            return;
        }
        mav.addObject("csp", request.getAttribute("csp"));
    }

    private boolean isRedirect(ModelAndView mav) {
        if (mav.getViewName() != null && mav.getViewName().startsWith("redirect:")) {
            return true;
        }
        return mav.getView() instanceof SmartView smartView && smartView.isRedirectView();
    }

}
