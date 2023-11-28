package net.optionfactory.spring.upstream;

import java.io.IOException;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.RequestContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

public interface UpstreamHttpRequestExecution {

    ResponseContext execute(InvocationContext invocation, RequestContext request) throws IOException;
}
