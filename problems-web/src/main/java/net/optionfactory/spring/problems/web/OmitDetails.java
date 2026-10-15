package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.optionfactory.spring.problems.Problem;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.springframework.web.method.HandlerMethod;

/// Clears the `details` of every problem, so that exception messages and other internal state do
/// not reach clients.
///
/// [RestExceptionResolver.Builder] adds it after every other transformer unless details are
/// included: register it yourself only on a resolver built with its constructor.
public class OmitDetails implements FailureTransformer {

    /// @param saf the status and problems
    /// @param request the current request, unused
    /// @param response the current response, unused
    /// @param handler the handler that threw, unused
    /// @param ex the exception, unused
    /// @return the status of `saf`, with copies of its problems without details
    @Override
    public RestExceptionResolver.HttpStatusAndProblems transform(HttpStatusAndProblems saf, HttpServletRequest request, HttpServletResponse response, HandlerMethod handler, Exception ex) {
        return new HttpStatusAndProblems(saf.status(), saf.problems().stream().map(p -> p.withDetails(Problem.NO_DETAILS)).toList());
    }

}
