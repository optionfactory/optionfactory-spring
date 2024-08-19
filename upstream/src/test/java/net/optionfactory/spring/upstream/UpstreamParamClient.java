package net.optionfactory.spring.upstream;

import java.util.Map;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.web.service.annotation.GetExchange;

public interface UpstreamParamClient {

    @GetExchange("/endpoint/{parb}/{b}")
    @Upstream.Endpoint("endpoint")
    @Upstream.Mock("mock.json")
    Map<String, String> testEndpoint(
            @Upstream.PathVariable(key = "parb", value = "#parameter")
            @Upstream.PathVariable(key = "b", value = "#{#parameter}", valueType = Expressions.Type.TEMPLATED) String parameter);

}
