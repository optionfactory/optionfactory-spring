package net.optionfactory.spring.problems.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.optionfactory.spring.problems.Problem;
import org.springframework.web.method.HandlerMethod;

public class OmitDetails implements ProblemTransformer {

    @Override
    public Problem transform(Problem problem, HttpServletRequest request, HttpServletResponse response, HandlerMethod handler, Exception ex) {
        problem.details = null;
        return problem;
    }

}
