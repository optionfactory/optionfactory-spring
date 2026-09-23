package net.optionfactory.spring.problems.web.upstream;

import java.util.List;
import net.optionfactory.spring.problems.web.FailureTransformer;
import net.optionfactory.spring.problems.web.ProblemsModule;

/// Built in when `upstream` is on the classpath: an upstream's failure is forwarded and its problems'
/// contexts rewritten as the handler declares with [UpstreamProblems.Forward] and
/// [UpstreamProblems.MapContext]. Inert on handlers declaring neither.
public class UpstreamProblemsModule implements ProblemsModule {

    @Override
    public List<FailureTransformer> transformers() {
        return List.of(new UpstreamFailureTransformer());
    }
}
