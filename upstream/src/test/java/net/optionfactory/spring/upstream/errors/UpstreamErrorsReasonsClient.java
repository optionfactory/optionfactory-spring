package net.optionfactory.spring.upstream.errors;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.expressions.Expressions;
import org.springframework.http.HttpStatus;
import org.springframework.web.service.annotation.GetExchange;

@Upstream("reasons-upstream")
public interface UpstreamErrorsReasonsClient {

    @GetExchange("/first")
    @Upstream.Endpoint("first-matching")
    @Upstream.ErrorOnResponse(value = "#response.headers().getFirst('X-Code') == 'a'", reason = "first: #{#response.headers().getFirst('X-Code')}")
    @Upstream.ErrorOnResponse(value = "true", reason = "second")
    Map<String, String> firstMatchingWins();

    @GetExchange("/redirects")
    @Upstream.ErrorOnResponse(value = "true", reason = "redirect", series = HttpStatus.Series.REDIRECTION)
    Map<String, String> onRedirectsOnly();

    @GetExchange("/expression-reason")
    @Upstream.ErrorOnResponse(value = "true", reason = "'computed ' + #response.status().value()", reasonType = Expressions.Type.EXPRESSION)
    Map<String, String> withExpressionReason();

    @GetExchange("/plain")
    Map<String, String> plain();
}
