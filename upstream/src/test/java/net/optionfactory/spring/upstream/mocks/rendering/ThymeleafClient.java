package net.optionfactory.spring.upstream.mocks.rendering;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import org.springframework.web.service.annotation.GetExchange;

@Upstream.Mock.DefaultContentType("application/json")
public interface ThymeleafClient {

    @GetExchange("/endpoint/")
    @Upstream.Endpoint("endpoint")
    @Upstream.Mock("mock.json.template")
    Map<String, String> testEndpoint(@Upstream.Context String param);

}
