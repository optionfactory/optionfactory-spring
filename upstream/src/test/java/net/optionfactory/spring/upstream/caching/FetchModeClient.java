package net.optionfactory.spring.upstream.caching;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream.Context;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

public interface FetchModeClient {

    public enum FetchMode{
        ANY, FRESH;
    }
    
    
    @GetExchange("/fetch/{para}/")
    Map<String, String> get(@PathVariable String para, @Context FetchMode mode);

}
