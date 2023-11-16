package net.optionfactory.spring.upstream.caching;

import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

public interface FetchModeClient {

    @GetExchange("/fetch/{para}/")
    Map<String, String> get(@PathVariable String para, FetchMode mode);

}
