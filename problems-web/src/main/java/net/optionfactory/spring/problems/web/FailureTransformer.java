package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.springframework.web.method.HandlerMethod;

/// Rewrites the answer to an exception after it has been classified: changes the status, adds,
/// drops or rewrites problems.
///
/// Transformers run in order on every exception the [RestExceptionResolver] answers, classified or
/// not, each given what the previous one returned: the built-in ones first, then those registered
/// with `RestExceptionResolver.Builder#withTransformer` or a [ProblemsModule], and finally, unless
/// details are included, [OmitDetails]. A transformer that does not apply to an exception returns
/// what it was given. To answer an exception the resolver does not know, register an
/// [ExceptionClassifier] instead: a transformer only sees it once it has been logged as an error.
public interface FailureTransformer {

    /// @param saf the status and problems answered so far; the problems may be modified in place
    /// @param request the current request
    /// @param response the current response
    /// @param handler the handler method that threw
    /// @param ex the exception being answered
    /// @return the status and problems to answer with, `saf` itself when nothing changes
    HttpStatusAndProblems transform(HttpStatusAndProblems saf, HttpServletRequest request, HttpServletResponse response, HandlerMethod handler, Exception ex);

}
