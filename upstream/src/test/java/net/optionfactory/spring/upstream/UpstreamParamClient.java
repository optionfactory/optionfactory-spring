package net.optionfactory.spring.upstream;

import java.util.Map;
import org.springframework.web.service.annotation.GetExchange;

public interface UpstreamParamClient {

    @GetExchange("/endpoint/{parb}")
    @Upstream.Endpoint("endpoint")
    @Upstream.Mock("mock.json")
    Map<String, String> test(
            @Upstream.Param(key = "parb", value = "#this", type = Upstream.Param.Type.PATH_VARIABLE)
            @Upstream.Param(key = "b", value = "#root") String parameter);

}
