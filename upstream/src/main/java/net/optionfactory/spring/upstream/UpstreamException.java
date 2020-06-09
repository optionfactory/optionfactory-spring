package net.optionfactory.spring.upstream;

import net.optionfactory.spring.problems.Failure;
import net.optionfactory.spring.problems.Problem;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_GATEWAY)
public class UpstreamException extends Failure {

    public UpstreamException(String upstreamId, String reason, String internalDetails) {
        super(Problem.of("UPSTREAM_PROBLEM", upstreamId, reason, internalDetails));
    }

}
