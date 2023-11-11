package net.optionfactory.spring.upstream.rest;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

@Upstream("rest-test")
public interface RestTestClient {

    @GetExchange("/200")
    Map<String, String> ok(@RequestParam String asd);

    @GetExchange("/400")
    Map<String, String> error(@RequestParam String asd);

}
