package net.optionfactory.spring.upstream.mocks;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.UpstreamEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

@Upstream(value = "mock-client")
@UpstreamMockStatus(HttpStatus.CREATED)
@UpstreamMockContentType("application/json;charset=utf-8")
public interface MockClient {

    @GetExchange("/endpoint/{parb}")
    @UpstreamEndpoint("endpoint")
    @UpstreamMock("#{upstream}-#{endpoint}-#{args.para}-#{args.parb}.json")
    ResponseEntity<Map<String, String>> add(@RequestParam String para, @PathVariable String parb);
}
