package net.optionfactory.spring.upstream.mocks.rendering;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.core.io.Resource;

/// Turns a mock resource into the body of a mocked response, e.g. by evaluating it as a template.
///
/// Registered through [net.optionfactory.spring.upstream.mocks.MocksCustomizer]: for each mock
/// resource the first renderer accepting it renders it. Renderers are called for every invocation,
/// possibly concurrently.
public interface MocksRenderer {

    /// @param source the mock resource
    /// @return true when this renderer handles the resource, usually decided on its file name
    boolean canRender(Resource source);

    /// @param source the mock resource
    /// @param ctx the invocation being mocked
    /// @return the response body
    Resource render(Resource source, InvocationContext ctx);

}
