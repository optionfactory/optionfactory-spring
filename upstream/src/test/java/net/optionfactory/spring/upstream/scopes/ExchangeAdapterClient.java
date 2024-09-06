package net.optionfactory.spring.upstream.scopes;

import net.optionfactory.spring.upstream.Upstream;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

@Upstream("exchange-adapter-test")
@Upstream.Logging
public interface ExchangeAdapterClient {

    @PostExchange("/")
    void adaptExchange(@RequestBody InnerBody request);

    @PostExchange("/")
    ResponseEntity<Void> adaptExchangeForBodilessEntity(@RequestBody InnerBody request);

    @PostExchange("/")
    String adaptExchangeForBody(@RequestBody InnerBody request);

    @PostExchange("/")
    ResponseEntity<String> adaptExchangeForEntity(@RequestBody InnerBody request);

    public record InnerBody(String key, String value) {

    }

    public record Wrapper<T>(T inner) {

    }

}
