package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.optionfactory.spring.problems.web.RestExceptionResolver.HttpStatusAndProblems;
import org.springframework.web.method.HandlerMethod;

public class OmitDetails implements FailureTransformer {

    @Override
    public RestExceptionResolver.HttpStatusAndProblems transform(HttpStatusAndProblems saf, HttpServletRequest request, HttpServletResponse response, HandlerMethod handler, Exception ex) {
        saf.problems().forEach(p -> p.details = null);
        return saf;
    }

}
