package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.DisconnectedClientHelper;

/// Declines to answer when there is no response left to answer with, so the
/// resolvers behind it are not asked to write one.
///
/// A streaming endpoint commits its response with the first byte it sends, and
/// the usual way such a stream ends is that the client goes away: every
/// navigation, tab close and sleeping laptop ends one. The resolvers behind this
/// one answer an exception by setting a status and rendering a body, neither of
/// which is possible once the response is committed, and the attempt throws a
/// second exception that buries the first.
public class UndeliverableResponseExceptionResolver implements HandlerExceptionResolver {

    private static final Logger logger = LoggerFactory.getLogger(UndeliverableResponseExceptionResolver.class);

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (DisconnectedClientHelper.isClientDisconnectedException(ex)) {
            logger.debug("client gone at {}: {}", request.getRequestURI(), ex.getMessage());
            return new ModelAndView();
        }
        if (response.isCommitted()) {
            logger.warn(String.format("error at %s after the response was committed, nothing can be sent to the client", request.getRequestURI()), ex);
            return new ModelAndView();
        }
        return null;
    }
}
