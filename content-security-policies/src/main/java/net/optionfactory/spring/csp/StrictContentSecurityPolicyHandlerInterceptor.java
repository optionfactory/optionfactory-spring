package net.optionfactory.spring.csp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.SmartView;

/// Copies the request's [StrictContentSecurityPolicyNonceFilter.Csp] into the model as `csp`, so
/// that templates can write the nonce on their scripts. Register it with
/// [StrictContentSecurityPolicy#addNonceToModel()].
///
/// Handlers that render no view (no `ModelAndView`, e.g. `@ResponseBody` ones) and redirects (a
/// `redirect:` view name, or a redirecting `SmartView` such as `RedirectView`) are left alone: a
/// redirect has no page to put a nonce on.
public class StrictContentSecurityPolicyHandlerInterceptor implements HandlerInterceptor {

    /// Adds the `csp` model attribute to a view that is about to be rendered.
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
