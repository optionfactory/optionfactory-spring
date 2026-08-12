package net.optionfactory.spring.upstream.mocks;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.annotation.GetExchange;

@Upstream("bad-header-client")
public interface BadHeaderMockClient {

    @GetExchange("/test")
    @Upstream.Endpoint("endpoint")
    @Upstream.Mock(value = "bad-header.json", headers = "no-colon-line")
    ResponseEntity<Map<String, String>> call();
}
