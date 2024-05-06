package net.optionfactory.spring.upstream.mocks;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

@Upstream("mock-client")
@Upstream.Mock.DefaultContentType("application/json;charset=utf-8")
public interface MockClient {

    @GetExchange("/endpoint/{parb}")
    @Upstream.Endpoint("endpoint")
    @Upstream.Mock(value="#{upstream}-#{endpoint}-#{args.para}-#{args.parb}.json", status=HttpStatus.CREATED)
    ResponseEntity<Map<String, String>> add(@RequestParam String para, @PathVariable String parb);

}
