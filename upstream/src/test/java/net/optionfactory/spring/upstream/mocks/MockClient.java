package net.optionfactory.spring.upstream.mocks;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

@Upstream(value = "mock-client")
@Upstream.MockStatus(HttpStatus.CREATED)
@Upstream.MockContentType("application/json;charset=utf-8")
public interface MockClient {

    @GetExchange("/endpoint/{parb}")
    @Upstream.Endpoint("endpoint")
    @Upstream.Mock("#{upstream}-#{endpoint}-#{args.para}-#{args.parb}.json")
    ResponseEntity<Map<String, String>> add(@RequestParam String para, @PathVariable String parb);

}
