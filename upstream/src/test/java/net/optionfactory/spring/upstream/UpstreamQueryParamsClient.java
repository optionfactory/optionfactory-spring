package net.optionfactory.spring.upstream;

import java.util.Map;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

public interface UpstreamQueryParamsClient {

    @GetExchange("/endpoint?q={qv}")
    @Upstream.Endpoint("endpoint")
    @Upstream.QueryParam(key = "extra", value = "v", valueType = Expressions.Type.STATIC)
    Map<String, String> keepsExistingQueryParams(@PathVariable String qv);

    @GetExchange("/endpoint?q={qv}")
    @Upstream.Endpoint("endpoint")
    @Upstream.QueryParam(key = "extra", value = "a b&c=d", valueType = Expressions.Type.STATIC)
    Map<String, String> encodesAddedQueryParams(@PathVariable String qv);

}
