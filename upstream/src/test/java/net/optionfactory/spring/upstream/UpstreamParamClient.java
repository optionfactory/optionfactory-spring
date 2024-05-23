package net.optionfactory.spring.upstream;

import java.util.Map;
import org.springframework.web.service.annotation.GetExchange;

public interface UpstreamParamClient {

    @GetExchange("/endpoint/{parb}/{b}")
    @Upstream.Endpoint("endpoint")
    @Upstream.Mock("mock.json")
    Map<String, String> testEndpoint(
            @Upstream.PathVariable(key = "parb", value = "#this")
            @Upstream.PathVariable(key = "b", value = "#root") String parameter);

}
