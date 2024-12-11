package net.optionfactory.spring.upstream.mocks.rendering;

import net.optionfactory.spring.upstream.contexts.InvocationContext;
import org.springframework.core.io.Resource;

public class StaticRenderer implements MocksRenderer {

    @Override
    public boolean canRender(Resource source) {
        return true;
    }
    
    @Override
    public Resource render(Resource source, InvocationContext ctx) {
        return source;
    }


}
