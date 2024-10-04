package net.optionfactory.spring.upstream.mocks.rendering;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.core.io.Resource;

public interface MocksRenderer {

    Resource render(Resource source, InvocationContext ctx);

}