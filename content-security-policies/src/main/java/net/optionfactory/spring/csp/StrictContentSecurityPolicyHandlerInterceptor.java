package net.optionfactory.spring.csp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class StrictContentSecurityPolicyHandlerInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mav) throws Exception {
        if (mav == null) {
            return;
        }
        mav.addObject("cspnonce", request.getAttribute("cspnonce"));
    }

}
