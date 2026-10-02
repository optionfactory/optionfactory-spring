package net.optionfactory.spring.upstream.errors;

import java.util.Map;
import net.optionfactory.spring.upstream.Upstream;
import org.springframework.http.HttpStatus;
import org.springframework.web.service.annotation.GetExchange;

@Upstream("repeated-on-type")
@Upstream.ErrorOnResponse(value = "#response.headers().getFirst('X-Code') == 'a'", reason = "type first")
@Upstream.ErrorOnResponse(value = "true", reason = "type second", series = {HttpStatus.Series.SUCCESSFUL, HttpStatus.Series.CLIENT_ERROR})
public interface UpstreamErrorsRepeatedOnTypeClient {

    @GetExchange("/inherited")
    Map<String, String> inherited();
}
