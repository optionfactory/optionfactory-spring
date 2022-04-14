package net.optionfactory.spring.upstream;

import java.util.Map;
import net.optionfactory.spring.upstream.UpstreamPort.Hints;
import org.springframework.http.HttpHeaders;

public class UpstreamTracingInterceptor<CTX> implements UpstreamInterceptor<CTX> {

    private final ContextHeadersEncoder<CTX> contextHeadersEncoder;
    private final String prefix;

    public UpstreamTracingInterceptor(ContextHeadersEncoder<CTX> contextHeadersEncoder, String prefix) {
        this.contextHeadersEncoder = contextHeadersEncoder;
        this.prefix = prefix;
    }

    @Override
    public HttpHeaders prepare(Hints<CTX> hints, PrepareContext<CTX> prepare) {
        final HttpHeaders headers = new HttpHeaders();
        contextHeadersEncoder.toMap(prepare.ctx).forEach((k, v) -> {
            headers.add(String.format("%s%s", prefix, k), v);
        });
        headers.set(String.format("%sREQID", prefix), prepare.requestId);
        return headers;
    }

    public interface ContextHeadersEncoder<CTX> {

        Map<String, String> toMap(CTX ctx);

        public static class Null<T> implements ContextHeadersEncoder<T> {

            @Override
            public Map<String, String> toMap(T ctx) {
                return Map.of();
            }

        }

    }

}
