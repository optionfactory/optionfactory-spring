package net.optionfactory.spring.upstream.mocks.rendering;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.core.io.Resource;

/// Serves mock resources as they are; the fallback used when no other renderer accepts a resource.
public class StaticRenderer implements MocksRenderer {

    /// @param source the mock resource
    /// @return always true
    @Override
    public boolean canRender(Resource source) {
        return true;
    }

    /// @param source the mock resource
    /// @param ctx the invocation, unused
    /// @return the resource itself
    @Override
    public Resource render(Resource source, InvocationContext ctx) {
        return source;
    }

}
